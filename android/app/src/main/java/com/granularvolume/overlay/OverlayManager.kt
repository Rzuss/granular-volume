package com.granularvolume.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.granularvolume.BuildConfig
import com.granularvolume.R
import com.granularvolume.audio.AudioController
import com.granularvolume.audio.FullRangeCoordinator
import com.granularvolume.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * Floating overlay — FULL-RANGE mode.
 *
 * One logical scale, two zones:
 *  - UPPER ZONE: dynamic bars (device 5 dB rungs for media / hardware steps for ring),
 *    normal system volume — the physical buttons' replacement.
 *  - the orange device-minimum line
 *  - QUIET ZONE: the classic 7 dB step bars (0 .. −30), behaviourally identical to 1.3.4.
 * Plus a media-mute toggle at the bottom (alarms survive — media-stream mute, never global).
 *
 * Touch architecture (unchanged from 1.3.4):
 *  - a SINGLE unified touch handler on the root makes the ENTIRE pill draggable from anywhere,
 *    while short taps are hit-tested to whatever control sits under the finger.
 *  - FLAG_NOT_TOUCH_MODAL keeps touches OUTSIDE the pill flowing to the app below.
 *
 * Bounds behaviour (hide off edges, never onto the Home keys) and idle dimming are unchanged.
 */
class OverlayManager(
    private val context: Context,
    private val audioController: AudioController,
    private val coordinator: FullRangeCoordinator,
    private val scope: CoroutineScope,
    private val onDismiss: () -> Unit,
    private val onInfo: () -> Unit
) {

    /**
     * Fired after every VOLUME gesture the dial handles: chevrons, bars, mute.
     * Deliberately NOT fired for close (a leaving user is never pitched on the way
     * out) or for the info button (it already opens the sheet this hook exists to
     * trigger). The service uses it for exactly one thing: the once-only warning on
     * the trial's final day, so a session that runs across the day boundary still
     * gets told before the lock, without a single spontaneous popup.
     */
    var onEngaged: (() -> Unit)? = null

    companion object {
        // Step index 0 = quietest (−30 dB), index 6 = no attenuation (0 dB, at the floor).
        val STEP_DB = floatArrayOf(-30f, -25f, -20f, -15f, -10f, -5f, 0f)

        /**
         * Highest quiet step the user can actually select: −5 dB (index 5).
         * Index 6 (0 dB) is the floor, already shown as the last upper rung, so its bar is
         * hidden and the ladder crosses straight from −5 dB to that rung.
         */
        private const val QUIET_TOP_VISIBLE = 5

        private const val DEFAULT_X = 24
        private const val DEFAULT_Y = 200
        private const val DRAG_SLOP_PX = 12
        private const val ANIM_MS = 120L
        private const val BOTTOM_VISIBLE_FRACTION = 4
        private const val IDLE_FADE_DELAY_MS = 3500L
        private const val IDLE_FADE_MS = 380L
        private const val WAKE_MS = 120L
        private const val IDLE_ALPHA = 0.4f
        private const val ACTIVE_ALPHA = 1.0f

        private const val ALPHA_CURRENT  = 1.00f
        private const val ALPHA_ACTIVE   = 0.50f
        private const val ALPHA_INACTIVE = 0.10f
        /** Quiet bars during a cellular call: dimmer than inactive, so the zone reads as
         *  unavailable rather than merely unselected. Still faintly visible, because the
         *  bars come back the moment the call ends and a gap in the dial would alarm. */
        private const val ALPHA_UNAVAILABLE = 0.04f

        // Upper-zone bar geometry: the container height is FIXED (84dp in XML) — only bar
        // density varies with the device's rung count, per the no-growth size cap.
        private const val UPPER_CONTAINER_DP = 84
        private const val UPPER_BAR_GAP_DP = 2
        private const val UPPER_BAR_MIN_DP = 3

        // 1.4.4 option B: manual hit-test slops, re-derived for the new geometry.
        // Chevron keys are 48x30 — 9dp slop makes the effective target 66x48 (≥48dp).
        // Mute bar is 56x24 — 12dp slop makes it 80x48. Contested slop space between the
        // down key and the mute bar is decided by TEST ORDER: chevrons before mute, because
        // the mis-tap option B exists to prevent is a chevron tap landing on mute.
        private const val CHEVRON_SLOP_DP = 9

        /**
         * 1.5.1 pilot 2: info sits alone at the top; 6dp of slop claims the gap down to the
         * window-control row and stops at its edge. It is hit-tested before that row, so a
         * finger aiming at info and landing a little low still gets the sheet, never close.
         */
        private const val INFO_SLOP_DP = 6
        /**
         * Minimize and close share one row, minimize on the left. Minimize is hit-tested
         * first with the wider slop, so the gap between them and any low miss from info
         * resolve to minimize: recoverable beats destructive.
         */
        private const val MINIMIZE_SLOP_DP = 12
        private const val MUTE_SLOP_DP = 12

        // 1.5.1 pilot 2: docking geometry. All dp values are build-time polish.
        private const val TAB_W_DP = 30            // painted D-shaped tab
        private const val TAB_H_DP = 72
        private const val TAB_WINDOW_W_DP = 44     // its touch window, fully on screen
        private const val TAB_WINDOW_H_DP = 88
        private const val TAB_TOP_MIN_DP = 96      // never higher than this from the top
        private const val TAB_NAV_GAP_DP = 160     // never lower than this above the nav bar
        /** Pull the tab this far inward and the dial comes out, without lifting the finger. */
        private const val TAB_PULL_DP = 40
        /** Release the dial with its near edge inside this band and it is pulled to the wall. */
        private const val SNAP_ZONE_DP = 64
        private const val SNAP_MS = 240L
        /** A drag that continues this far past the wall (the dial is already flush) collapses it. */
        private const val PUSH_THROUGH_DP = 28
        private const val PREVIEW_SCALE = 0.85f
        private const val PREVIEW_ALPHA = 0.7f
        private const val MORPH_MS = 200L
        private const val MORPH_OUT_MS = 130L
        private const val MORPH_SCALE = 0.35f
        private const val TAB_IDLE_ALPHA = 0.6f

        // 1.5.1 feature tour
        private const val TOUR_STEPS = 4
        private const val TOUR_START_DELAY_MS = 700L

        // 1.4.4 press feedback + key-press flash.
        private const val PRESS_SCALE = 0.96f
        private const val PRESS_MS = 120L
        // Flash starts after render()'s ANIM_MS alpha animations settle, so the two never
        // fight over the same View's animator.
        private const val FLASH_DELAY_MS = 140L
        private const val FLASH_IN_MS = 90L
        private const val FLASH_HOLD_MS = 60L
        private const val FLASH_OUT_MS = 220L
        /** Gap between neighbouring bars in the purchase wave; the whole ladder takes ~0.9 s. */
        private const val SWEEP_STAGGER_MS = 28L

        private const val COLOR_LABEL_NORMAL = 0x88FFFFFF.toInt()
        private const val COLOR_MUTED_ACCENT = 0xFFFF5A5F.toInt()
        private const val COLOR_MUTE_CONTENT = 0x99FFFFFF.toInt()
        private const val COLOR_MUTE_INVERTED = 0xFF1A1A2E.toInt()
    }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density
    private val dismissHitSlop = (12 * density).toInt()
    private val infoHitSlop = (INFO_SLOP_DP * density).toInt()
    private val minimizeHitSlop = (MINIMIZE_SLOP_DP * density).toInt()
    private val chevronHitSlop = (CHEVRON_SLOP_DP * density).toInt()
    private val muteHitSlop = (MUTE_SLOP_DP * density).toInt()
    private var overlayView: View? = null
    private var flowJob: Job? = null

    // 1.5.1 Quiet Blade: the collapsed form is a second, smaller window. At most one of
    // overlayView / bladeRoot exists at a time; the coordinator flows keep feeding whichever
    // is up, so attenuation and the level readout never stop while the dial is away.
    private var bladeRoot: FrameLayout? = null
    private var bladeView: DockTabView? = null
    private var bladeOnRight = false
    private var bladeDimAnimator: ValueAnimator? = null
    private val bladeParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }
    private val bladeIdleRunnable = Runnable { animateBladeDim(TAB_IDLE_ALPHA, IDLE_FADE_MS) }

    // Push-through state, live only during a drag of the dial.
    private var pushArmed = false
    private var pushToRight = false
    private var lastMinX = 0
    private var lastMaxX = 0

    /** Quiet-zone step currently selected (meaningful only while zoneQuiet). */
    private var currentStep = STEP_DB.size - 1

    /** Last value announced to a screen reader, so an unchanged re-render stays silent. */
    private var lastSpokenLevel: String? = null

    private val upperBars = ArrayList<View>()

    private val idleFadeRunnable = Runnable {
        overlayView?.animate()?.alpha(IDLE_ALPHA)?.setDuration(IDLE_FADE_MS)?.start()
    }

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = Prefs.getOverlayX(context, DEFAULT_X)
        y = Prefs.getOverlayY(context, DEFAULT_Y)
    }

    /**
     * Attach the overlay in whichever form the user left it: the dial, or (1.5.1) the blade
     * if it was minimized when the service last stopped, so a restart or a reboot brings
     * back the same picture. Throws on failure so the caller can report it.
     */
    fun show() {
        if (overlayView != null || bladeRoot != null) return
        attached = true
        if (Prefs.isCollapsed(context)) showTab(animateIn = false) else showDial()

        // One render pipeline for BOTH forms: any state change (gain flow or coordinator
        // revision) re-renders whichever surface is up. Started once per show/hide cycle,
        // never per form swap, so a collapse cannot leak a collector.
        if (flowJob == null) {
            flowJob = scope.launch(Dispatchers.Main) {
                launch { audioController.attenuationDb.collect { renderCurrent() } }
                launch { coordinator.uiRevision.collect { renderCurrent() } }
                // 1.4.4: key-press flash acknowledgements (id 0 is the initial empty signal).
                launch {
                    coordinator.flash.collect { sig ->
                        val v = overlayView
                        if (v != null && sig.id > 0 && sig.target != null) onFlash(v, sig.target)
                    }
                }
            }
        }
    }

    private fun showDial(entranceFromRight: Boolean = false, animateEntrance: Boolean = false) {
        val themedCtx = ContextThemeWrapper(context, R.style.Theme_GranularVolume)
        val view = LayoutInflater.from(themedCtx).inflate(R.layout.overlay_slider, null)
        overlayView = view
        // A fresh view has an empty upper container: the old bars belong to the view that was
        // just discarded, so the cache is dropped before setupView builds them again.
        upperBars.clear()
        setupView(view)
        // Home position: the last place the user left the dial. Not touched while collapsed,
        // which is exactly why the tab can bring it back to the same spot.
        layoutParams.x = Prefs.getOverlayX(context, DEFAULT_X)
        layoutParams.y = Prefs.getOverlayY(context, DEFAULT_Y)
        wm.addView(view, layoutParams)

        // Entrance from the tab: the dial springs out of the wall it was folded into. Set
        // before the first frame so there is no flash of the full dial.
        val animateIn = animateEntrance && animationsEnabled()
        if (animateIn) {
            view.scaleX = MORPH_SCALE
            view.scaleY = 0.92f
            view.alpha = 0f
        }

        view.post {
            if (clampToBounds(view)) applyLayout()
            if (animateIn) {
                view.pivotX = if (entranceFromRight) view.width.toFloat() else 0f
                view.pivotY = view.height / 2f
                view.animate().scaleX(1f).scaleY(1f).alpha(ACTIVE_ALPHA).setDuration(MORPH_MS)
                    .setInterpolator(OvershootInterpolator(1.2f)).start()
            }
            // The dial may sit flush against a wall, inside the back-gesture inset; exclude
            // its own rect so a drag that starts there is ours (the system trims to 200dp).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.systemGestureExclusionRects = listOf(Rect(0, 0, view.width, view.height))
            }
            scheduleIdleFade(view)
            // Harness hook (A34): the upper zone must be populated in the view that is on screen.
            android.util.Log.d("GranularVolume", "dial shown: upper bars in container=" +
                view.findViewById<LinearLayout>(R.id.gv_upper_container).childCount)
        }
    }

    private fun renderCurrent() {
        overlayView?.let { render(it) }
        if (bladeView != null) renderBlade()
    }

    fun hide() {
        // Order matters: mark detached FIRST, so a morph whose end action is already queued
        // finds the manager closed; then cancel the animators so most end actions never run.
        attached = false
        pendingCelebration = false
        snapAnimator?.cancel()
        overlayView?.animate()?.cancel()
        bladeRoot?.animate()?.cancel()
        morphing = false
        endTour(animated = false)
        flowJob?.cancel()
        flowJob = null
        overlayView?.let {
            it.removeCallbacks(idleFadeRunnable)
            runCatching { wm.removeView(it) }
            overlayView = null
        }
        removeBladeWindow()
    }

    // ────────────────────────────────────────────────────────────────
    // 1.5.1 pilot 2: suction to the wall, collapse to a "+" tab, and restore
    // ────────────────────────────────────────────────────────────────

    private var snapAnimator: ValueAnimator? = null
    private var morphing = false

    /**
     * 1.5.3: true between show() and hide(). The morph animations end in a callback that adds
     * the next window (tab or dial); if the service stopped during those 130-200 ms, that
     * callback used to attach a window to a manager whose service was already gone, and the
     * overlay outlived the control. Every morph end now checks this first.
     */
    private var attached = false

    /**
     * 1.5.3: the key arrived while the dial was docked or mid-morph. The celebration needs the
     * dial on screen, so it is remembered here and played once the dial has come back.
     */
    private var pendingCelebration = false

    /**
     * Magnetic docking. Called when a drag ends: if the dial's near edge is within
     * SNAP_ZONE_DP of a side wall it glides flush to that wall, with one tick. Outside the
     * zone it stays where it was dropped, so free placement survives and only the last
     * stretch is pulled in. The wall is where a later push-through or minimize docks from.
     */
    private fun snapIfNearWall(root: View) {
        val screen = fullDisplayBounds()
        val zone = (SNAP_ZONE_DP * density).toInt()
        val w = root.width
        val leftGap = layoutParams.x
        val rightGap = screen.width() - w - layoutParams.x
        val targetX = when {
            leftGap <= zone && leftGap <= rightGap -> 0
            rightGap <= zone -> screen.width() - w
            else -> return
        }
        if (targetX == layoutParams.x) return
        animateDialX(root, targetX) { tick(root); savePosition() }
    }

    private fun animateDialX(root: View, targetX: Int, onEnd: () -> Unit) {
        snapAnimator?.cancel()
        if (!animationsEnabled()) { layoutParams.x = targetX; applyLayout(); onEnd(); return }
        snapAnimator = ValueAnimator.ofInt(layoutParams.x, targetX).apply {
            duration = SNAP_MS
            interpolator = DecelerateInterpolator(2.2f)
            addUpdateListener { layoutParams.x = it.animatedValue as Int; applyLayout() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { if (overlayView === root) onEnd() }
            })
            start()
        }
    }

    /**
     * Collapse the dial into the tab on the given edge. The dial's (x, y) is saved first and
     * is what [expand] returns to, exactly. The dial shrinks toward the wall and fades; the
     * tab then springs out of the same spot. Attenuation is untouched: this is a view swap.
     */
    private fun collapse(onRight: Boolean) {
        val dial = overlayView ?: return
        if (morphing) return
        endTour()
        morphing = true
        snapAnimator?.cancel()
        savePosition()
        val centerY = layoutParams.y + dial.height / 2
        val winH = (TAB_WINDOW_H_DP * density).toInt()
        Prefs.setBladePlacement(context, clampTabY(centerY - winH / 2), onRight)
        Prefs.setCollapsed(context, true)
        dial.removeCallbacks(idleFadeRunnable)
        pushArmed = false

        val finish = finish@{
            runCatching { wm.removeView(dial) }
            if (overlayView === dial) overlayView = null
            morphing = false
            // The service stopped during the morph: hide() already cleaned up; add nothing.
            if (!attached) return@finish
            showTab(animateIn = true)
            bladeRoot?.let { confirmHaptic(it) }
            // The key arrived mid-collapse: bring the dial straight back for the celebration.
            if (pendingCelebration) { expand(); return@finish }
            // One-time tip on the first collapse, as a plain text toast: the tab has no room
            // for a callout, and text toasts are allowed from a foreground service.
            if (!Prefs.wasBladeTipShown(context)) {
                Prefs.setBladeTipShown(context)
                runCatching { Toast.makeText(context, R.string.gv_blade_tip, Toast.LENGTH_LONG).show() }
            }
        }
        dial.animate().cancel()
        if (!animationsEnabled()) { finish(); return }
        dial.pivotX = if (onRight) dial.width.toFloat() else 0f
        dial.pivotY = (centerY - layoutParams.y).toFloat()
        dial.animate().scaleX(MORPH_SCALE).scaleY(0.92f).alpha(0f).setDuration(MORPH_MS)
            .setInterpolator(AccelerateInterpolator(1.4f)).withEndAction { finish() }.start()
    }

    /** "She comes home": the tab folds away and the dial springs back to its saved (x, y). */
    private fun expand() {
        val tab = bladeRoot ?: return
        if (morphing) return
        morphing = true
        Prefs.setCollapsed(context, false)
        val onRight = bladeOnRight
        val finish = finish@{
            removeBladeWindow()
            morphing = false
            if (!attached) return@finish
            showDial(entranceFromRight = onRight, animateEntrance = true)
            overlayView?.let { v ->
                confirmHaptic(v)
                // Play the purchase wave only after the entrance has settled, so the two
                // animations never fight over the same view.
                if (pendingCelebration) {
                    pendingCelebration = false
                    v.postDelayed({ if (overlayView === v) celebrateUnlock() }, MORPH_MS + 60L)
                }
            }
        }
        tab.animate().cancel()
        if (!animationsEnabled()) { finish(); return }
        tab.pivotX = if (onRight) tab.width.toFloat() else 0f
        tab.pivotY = tab.height / 2f
        tab.animate().scaleX(MORPH_SCALE).alpha(0f).setDuration(MORPH_OUT_MS)
            .setInterpolator(AccelerateInterpolator()).withEndAction { finish() }.start()
    }

    private fun showTab(animateIn: Boolean) {
        val winW = (TAB_WINDOW_W_DP * density).toInt()
        val winH = (TAB_WINDOW_H_DP * density).toInt()
        val screen = fullDisplayBounds()
        bladeOnRight = Prefs.isBladeOnRight(context, false)

        val root = FrameLayout(context)
        val tab = DockTabView(context).apply {
            tabWidthPx = TAB_W_DP * density
            tabHeightPx = TAB_H_DP * density
            onRight = bladeOnRight
            contentDescription = context.getString(R.string.gv_blade_desc)
        }
        root.addView(tab, FrameLayout.LayoutParams(winW, winH))
        bladeRoot = root
        bladeView = tab

        bladeParams.width = winW
        bladeParams.height = winH
        bladeParams.x = if (bladeOnRight) screen.width() - winW else 0
        bladeParams.y = clampTabY(Prefs.getBladeY(context, screen.height() / 3))
        wm.addView(root, bladeParams)

        if (animateIn && animationsEnabled()) {
            root.pivotX = if (bladeOnRight) winW.toFloat() else 0f
            root.pivotY = winH / 2f
            root.scaleX = MORPH_SCALE
            root.alpha = 0f
            root.animate().scaleX(1f).alpha(1f).setDuration(MORPH_MS)
                .setInterpolator(OvershootInterpolator(1.6f)).start()
        }

        // The tab lives where the back gesture lives. Excluding its own rect is the same
        // mechanism an edge panel uses; Android caps the exclusion at 200dp per edge and
        // this window is 88dp, so the request is honoured in full.
        root.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                root.systemGestureExclusionRects = listOf(Rect(0, 0, root.width, root.height))
            }
        }

        setupTabTouch(root)
        renderBlade()
        scheduleBladeIdle()
    }

    private fun removeBladeWindow() {
        bladeRoot?.let {
            it.removeCallbacks(bladeIdleRunnable)
            it.animate().cancel()
            bladeDimAnimator?.cancel()
            bladeDimAnimator = null
            runCatching { wm.removeView(it) }
        }
        bladeRoot = null
        bladeView = null
    }

    /**
     * Tap the plus, or pull the tab inward, and the dial comes back. A vertical drag moves
     * the tab within the spec's band.
     */
    private fun setupTabTouch(root: View) {
        var initialY = 0
        var downRawX = 0f
        var downRawY = 0f
        var dragging = false
        val pullPx = (TAB_PULL_DP * density).toInt()
        root.setOnTouchListener { _, e ->
            if (bladeRoot !== root) return@setOnTouchListener true
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    wakeBlade()
                    bladeView?.pressedLook = true
                    initialY = bladeParams.y
                    downRawX = e.rawX
                    downRawY = e.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downRawX).toInt()
                    val dy = (e.rawY - downRawY).toInt()
                    val inward = if (bladeOnRight) -dx else dx
                    if (inward >= pullPx) { expand(); return@setOnTouchListener true }
                    if (!dragging && abs(dy) > DRAG_SLOP_PX) { dragging = true; bladeView?.pressedLook = false }
                    if (dragging) {
                        bladeParams.y = clampTabY(initialY + dy)
                        runCatching { wm.updateViewLayout(root, bladeParams) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    bladeView?.pressedLook = false
                    if (dragging) {
                        Prefs.setBladePlacement(context, bladeParams.y, bladeOnRight)
                        scheduleBladeIdle()
                    } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                        expand()
                    }
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    /**
     * The tab's picture of the level, over the COMBINED scale the chevrons walk: upper
     * rungs first, then the visible quiet steps. Fill rises from the bottom to the current
     * position; the tick sits where the last upper rung meets the first quiet step, which is
     * where the dial draws its orange line.
     */
    private fun renderBlade() {
        val tab = bladeView ?: return
        val s = coordinator.uiState()
        val quietVisible = QUIET_TOP_VISIBLE + 1
        val total = (s.upperCount + quietVisible).coerceAtLeast(1)
        val index = if (s.zoneQuiet) {
            val step = dbToStep(s.quietDb).coerceAtMost(QUIET_TOP_VISIBLE)
            s.upperCount + (QUIET_TOP_VISIBLE - step)
        } else {
            s.upperPos.coerceIn(0, total - 1)
        }
        tab.update(
            fill = (total - index).toFloat() / total,
            tick = s.upperCount.toFloat() / total,
            muted = s.muted,
            locked = coordinator.lockedDisplayProvider()
        )
    }

    /** Spec band: never higher than 96dp from the top, never lower than 160dp above the nav bar. */
    private fun clampTabY(y: Int): Int {
        val winH = (TAB_WINDOW_H_DP * density).toInt()
        val screen = fullDisplayBounds()
        val minY = (TAB_TOP_MIN_DP * density).toInt()
        val maxY = max(minY, screen.height() - navBarHeight() - (TAB_NAV_GAP_DP * density).toInt() - winH)
        return y.coerceIn(minY, maxY)
    }

    private fun wakeBlade() {
        bladeRoot?.removeCallbacks(bladeIdleRunnable)
        animateBladeDim(ACTIVE_ALPHA, WAKE_MS)
    }

    private fun scheduleBladeIdle() {
        bladeRoot?.let {
            it.removeCallbacks(bladeIdleRunnable)
            it.postDelayed(bladeIdleRunnable, IDLE_FADE_DELAY_MS)
        }
    }

    /** Drawn dim rather than view alpha, so the orange tick stays at full opacity (spec). */
    private fun animateBladeDim(target: Float, durationMs: Long) {
        val tab = bladeView ?: return
        bladeDimAnimator?.cancel()
        if (!animationsEnabled()) { tab.dim = target; return }
        bladeDimAnimator = ValueAnimator.ofFloat(tab.dim, target).apply {
            duration = durationMs
            addUpdateListener { tab.dim = it.animatedValue as Float }
            start()
        }
    }

    // ────────────────────────────────────────────────────────────────
    // 1.5.1 feature tour: four callouts on the LIVE dial, once per install or update
    // ────────────────────────────────────────────────────────────────

    private var tourView: TourView? = null
    private var tourStep = 0
    private val tourRing = TourRingDrawable(density)
    private var ringAnimator: ValueAnimator? = null
    private val tourParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    /**
     * Once per install or update, on a start the PERSON made: the service calls this only
     * off a real start intent, never off the boot receiver (the anti-adware rule). Also never
     * when the range is locked, never during a cellular call, and only over the open dial.
     */
    fun maybeStartTour() {
        if (Prefs.getTourShownVersion(context) >= BuildConfig.VERSION_CODE) return
        val dial = overlayView ?: return
        if (coordinator.lockedDisplayProvider() || coordinator.uiState().quietUnavailable) return
        dial.postDelayed({
            if (overlayView === dial && tourView == null && !morphing) startTour()
        }, TOUR_START_DELAY_MS)
    }

    /** Replay from the info sheet: opens the dial first if it is docked. */
    fun startTourOnRequest() {
        if (bladeRoot != null) {
            expand()
            bladeRoot ?: overlayView?.postDelayed({ startTour() }, MORPH_MS + MORPH_OUT_MS + 120L)
            return
        }
        startTour()
    }

    private fun startTour() {
        val dial = overlayView ?: return
        if (tourView != null) return
        Prefs.setTourShownVersion(context, BuildConfig.VERSION_CODE)
        // The tour replaces the old one-line hint next to the line.
        Prefs.setLineTooltipShown(context)
        dial.findViewById<TextView>(R.id.gv_line_tooltip)?.visibility = View.GONE

        val tv = TourView(
            context,
            onSkip = { endTour() },
            onNext = { if (tourStep >= TOUR_STEPS) endTour() else showTourStep(tourStep + 1) }
        )
        tourView = tv
        tv.alpha = 0f
        // Cover the status bar too. Overlay windows are laid out below it by default; on
        // API 30+ the window can simply opt out of fitting any inset. (A negative y offset
        // with NO_LIMITS also draws there, but it broke touch delivery in the pilot.)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) tourParams.fitInsetsTypes = 0
        wm.addView(tv, tourParams)
        // The dial must sit ABOVE the scrim and stay live, so it is re-added as the newest
        // window. Same view, same params, same listeners; only the z-order changes.
        runCatching { wm.removeView(dial) }
        wm.addView(dial, layoutParams)
        dial.removeCallbacks(idleFadeRunnable)
        dial.animate().cancel()
        dial.alpha = ACTIVE_ALPHA
        dial.overlay.add(tourRing)
        tv.animate().alpha(1f).setDuration(220L).start()
        tv.post { showTourStep(1) }
    }

    private fun tourTargets(dial: View, step: Int): List<View> = when (step) {
        1 -> listOf(dial.findViewById(R.id.gv_upper_container))
        2 -> listOf(dial.findViewById(R.id.gv_divider), dial.findViewById(R.id.gv_steps_container))
        3 -> listOf(dial.findViewById(R.id.gv_btn_mute))
        else -> listOf(dial.findViewById(R.id.gv_btn_minimize))
    }

    private fun showTourStep(step: Int) {
        val dial = overlayView ?: run { endTour(); return }
        val tv = tourView ?: return
        tourStep = step
        val dialLoc = IntArray(2); dial.getLocationOnScreen(dialLoc)
        val dialRect = Rect(dialLoc[0], dialLoc[1], dialLoc[0] + dial.width, dialLoc[1] + dial.height)
        var target: Rect? = null
        for (v in tourTargets(dial, step)) {
            val l = IntArray(2); v.getLocationOnScreen(l)
            val r = Rect(l[0], l[1], l[0] + v.width, l[1] + v.height)
            target = target?.apply { union(r) } ?: r
        }
        val t = target ?: dialRect
        val titles = intArrayOf(R.string.gv_tour_1_title, R.string.gv_tour_2_title, R.string.gv_tour_3_title, R.string.gv_tour_4_title)
        val bodies = intArrayOf(R.string.gv_tour_1_body, R.string.gv_tour_2_body, R.string.gv_tour_3_body, R.string.gv_tour_4_body)
        val cardOnRight = dialRect.centerX() < fullDisplayBounds().width() / 2
        tv.showStep(
            step, TOUR_STEPS,
            context.getString(titles[step - 1]), context.getString(bodies[step - 1]),
            t, dialRect, cardOnRight, animate = animationsEnabled()
        )

        // Ring the element inside the dial; between steps the ring glides to its new home.
        val pad = (4 * density).toInt()
        val ringTo = Rect(t.left - dialLoc[0] - pad, t.top - dialLoc[1] - pad,
                          t.right - dialLoc[0] + pad, t.bottom - dialLoc[1] + pad)
        ringAnimator?.cancel()
        val from = Rect(tourRing.bounds)
        if (from.isEmpty || !animationsEnabled()) {
            tourRing.bounds = ringTo; dial.invalidate()
        } else {
            ringAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 260L
                interpolator = DecelerateInterpolator(1.8f)
                addUpdateListener {
                    val f = it.animatedValue as Float
                    tourRing.setBounds(
                        (from.left + (ringTo.left - from.left) * f).toInt(),
                        (from.top + (ringTo.top - from.top) * f).toInt(),
                        (from.right + (ringTo.right - from.right) * f).toInt(),
                        (from.bottom + (ringTo.bottom - from.bottom) * f).toInt()
                    )
                    dial.invalidate()
                }
                start()
            }
        }
    }

    private fun endTour(animated: Boolean = true) {
        val tv = tourView ?: return
        tourView = null
        ringAnimator?.cancel()
        overlayView?.let {
            it.overlay.remove(tourRing)
            tourRing.setBounds(0, 0, 0, 0)
            it.invalidate()
            scheduleIdleFade(it)
        }
        if (animated && animationsEnabled()) tv.fadeOut { runCatching { wm.removeView(tv) } }
        else runCatching { wm.removeView(tv) }
    }

    // ────────────────────────────────────────────────────────────────
    // Setup
    // ────────────────────────────────────────────────────────────────

    private fun setupView(view: View) {
        val quietBars  = collectStepBars(view)
        val btnUp      = view.findViewById<ImageButton>(R.id.gv_btn_up)
        val btnDown    = view.findViewById<ImageButton>(R.id.gv_btn_down)
        val btnDismiss = view.findViewById<ImageButton>(R.id.gv_btn_dismiss)
        val btnInfo    = view.findViewById<ImageButton>(R.id.gv_btn_info)
        val btnMinimize = view.findViewById<ImageButton>(R.id.gv_btn_minimize)
        // 1.4.4: the mute control is a full-width bar (LinearLayout), no longer an ImageButton.
        val btnMute    = view.findViewById<View>(R.id.gv_btn_mute)

        buildUpperBars(view)

        // Disable child click handling so EVERY touch reaches the root unified handler.
        for (b in quietBars) { b.isClickable = false; b.isFocusable = false }
        btnUp.isClickable = false
        btnDown.isClickable = false
        btnDismiss.isClickable = false
        btnInfo.isClickable = false
        btnMinimize.isClickable = false
        btnMute.isClickable = false

        // One-time hint next to the line (full-range onboarding spec: this is ALL of it).
        if (!Prefs.wasLineTooltipShown(context)) {
            view.findViewById<TextView>(R.id.gv_line_tooltip).visibility = View.VISIBLE
        }

        setupUnifiedTouch(view, quietBars, btnUp, btnDown, btnDismiss, btnInfo, btnMinimize, btnMute)
        render(view)
    }

    /**
     * Creates the upper-zone bars for the CURRENT device/mode. Bar height is computed so the
     * fixed 84dp container is always exactly filled — density varies, footprint never does.
     * Re-run whenever the rung count changes (route change, media/ring mode flip).
     */
    private fun buildUpperBars(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.gv_upper_container)
        val count = coordinator.upperPositionCount().coerceAtLeast(1)
        // The cache is valid only if the bars live in THIS view's container. Since 1.5.1 the
        // dial is inflated afresh every time it comes back from the tab, and until 1.5.2 this
        // check compared the count alone: the new container stayed empty while the list still
        // held the bars of the discarded view, so the upper zone rendered blank after every
        // restore (owner's phone, 2026-09-24, right after the key arrived).
        if (count == upperBars.size && upperBars.firstOrNull()?.parent === container) return
        container.removeAllViews()
        upperBars.clear()

        val gapPx = (UPPER_BAR_GAP_DP * density).toInt()
        val totalPx = (UPPER_CONTAINER_DP * density).toInt()
        val barPx = max(
            (UPPER_BAR_MIN_DP * density).toInt(),
            (totalPx - gapPx * (count - 1)) / count
        )

        // One name for the whole rung stack. Each bar previously carried the SAME description,
        // so a screen reader read "Volume level" once per rung before reaching anything useful.
        container.contentDescription = container.context.getString(R.string.gv_upper_bar_desc)

        for (i in 0 until count) {
            val bar = View(container.context).apply {
                background = container.context.getDrawable(R.drawable.bg_step_bar)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                isClickable = false
                isFocusable = false
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, barPx)
            if (i < count - 1) lp.bottomMargin = gapPx
            container.addView(bar, lp)
            upperBars.add(bar)
        }
    }

    private fun setupUnifiedTouch(
        root: View,
        quietBars: Array<View>,
        btnUp: View,
        btnDown: View,
        btnDismiss: ImageButton,
        btnInfo: ImageButton,
        btnMinimize: ImageButton,
        btnMute: View
    ) {
        var initialX = 0
        var initialY = 0
        var downRawX = 0f
        var downRawY = 0f
        var dragging = false
        val pushPx = (PUSH_THROUGH_DP * density).toInt()

        root.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    wake(root)
                    dismissTooltipIfShown(root)
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    downRawX = e.rawX
                    downRawY = e.rawY
                    dragging = false
                    pushArmed = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downRawX).toInt()
                    val dy = (e.rawY - downRawY).toInt()
                    if (!dragging && (abs(dx) > DRAG_SLOP_PX || abs(dy) > DRAG_SLOP_PX)) {
                        dragging = true
                    }
                    if (dragging) {
                        val wantedX = initialX + dx
                        layoutParams.x = wantedX
                        layoutParams.y = initialY + dy
                        clampToBounds(root)
                        applyLayout()
                        // 1.5.1 push-through: today's clamp stops the dial exactly where it
                        // always did. Only a drag that keeps going OUTWARD beyond that wall,
                        // by PUSH_THROUGH_DP, arms a collapse (live preview + one haptic), and
                        // easing back disarms it. Territory beyond the clamp was unreachable
                        // before, so the gesture cannot be hit by accident.
                        val overLeft = lastMinX - wantedX
                        val overRight = wantedX - lastMaxX
                        val over = max(overLeft, overRight)
                        if (!pushArmed && over >= pushPx) {
                            pushArmed = true
                            pushToRight = overRight > overLeft
                            tick(root)
                            root.pivotX = if (pushToRight) root.width.toFloat() else 0f
                            root.pivotY = root.height / 2f
                            root.animate().scaleX(PREVIEW_SCALE).alpha(PREVIEW_ALPHA)
                                .setDuration(WAKE_MS).start()
                        } else if (pushArmed && over < pushPx / 2) {
                            pushArmed = false
                            root.animate().scaleX(1f).alpha(ACTIVE_ALPHA).setDuration(WAKE_MS).start()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        clampToBounds(root)
                        applyLayout()
                        savePosition()
                        if (pushArmed && e.actionMasked == MotionEvent.ACTION_UP) {
                            root.animate().cancel()
                            root.scaleX = 1f
                            root.alpha = ACTIVE_ALPHA
                            dragging = false
                            collapse(pushToRight)
                            return@setOnTouchListener true
                        }
                        if (pushArmed) {
                            pushArmed = false
                            root.animate().scaleX(1f).alpha(ACTIVE_ALPHA).setDuration(WAKE_MS).start()
                        }
                        snapIfNearWall(root)
                    } else {
                        handleTap(root, e.rawX, e.rawY, quietBars, btnUp, btnDown, btnDismiss, btnInfo, btnMinimize, btnMute)
                    }
                    if (overlayView === root) scheduleIdleFade(root)
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 1.4.4 hit-test order, re-derived for the option B geometry: dismiss, then BOTH
     * chevrons, then mute, then bars. Chevrons win any contested slop space against the
     * mute bar — the mis-tap this design exists to prevent is a chevron tap landing on
     * mute, and an accidental mute is worse than an accidental step.
     */
    private fun handleTap(
        root: View,
        rawX: Float,
        rawY: Float,
        quietBars: Array<View>,
        btnUp: View,
        btnDown: View,
        btnDismiss: ImageButton,
        btnInfo: ImageButton,
        btnMinimize: ImageButton,
        btnMute: View
    ) {
        // ORDER IS THE DESIGN, and it has changed twice, each time for one reason.
        //
        // The chevrons come FIRST so that adding the meta buttons could not shave a
        // pixel off the primary control: volume is what this app is, and its regions are
        // exactly what they were. Close and the chevrons cannot contest each other at all,
        // because the second row sits between them.
        //
        // 1.5.1: minimize is tested next, before info and before close. It sits under
        // close's right half and the centre line, with close's own 12dp of slop, so the
        // band below close belongs to it: the finger that aims at close and lands low
        // (the 2026-09-24 one-star review, "I keep hitting ! when I press x") now
        // minimizes. That is recoverable with one tap on the blade, and it is close to
        // what the finger wanted. Info then beats close for the same reason it always did:
        // an accidental sheet is a tap to dismiss, an accidental close takes the dial away.
        if (hit(btnUp, rawX, rawY, chevronHitSlop)) {
            pressPulse(btnUp); tick(root); stepCombined(+1); onEngaged?.invoke(); return
        }
        if (hit(btnDown, rawX, rawY, chevronHitSlop)) {
            pressPulse(btnDown); tick(root); stepCombined(-1); onEngaged?.invoke(); return
        }
        if (hit(btnInfo, rawX, rawY, infoHitSlop)) {
            flash(btnInfo); onInfo(); return
        }
        if (hit(btnMinimize, rawX, rawY, minimizeHitSlop)) {
            flash(btnMinimize)
            val onRight = layoutParams.x + root.width / 2 > fullDisplayBounds().width() / 2
            collapse(onRight); return
        }
        if (hit(btnDismiss, rawX, rawY, dismissHitSlop)) {
            flash(btnDismiss); onDismiss(); return
        }
        if (hit(btnMute, rawX, rawY, muteHitSlop)) {
            pressPulse(btnMute); confirmHaptic(root); coordinator.toggleMute(); onEngaged?.invoke(); return
        }
        for (i in upperBars.indices) {
            if (hit(upperBars[i], rawX, rawY)) { tick(root); coordinator.applyUpper(i); onEngaged?.invoke(); return }
        }
        for (i in quietBars.indices) {
            if (hit(quietBars[i], rawX, rawY)) { tick(root); selectQuiet(i); onEngaged?.invoke(); return }
        }
    }

    /**
     * Chevron stepping across the COMBINED scale: upper rungs, then the quiet steps.
     *
     * The device floor is rendered exactly ONCE — as the last upper rung — so quiet bar 6
     * (0 dB) is hidden (see the layout comment). Every press therefore moves the highlight
     * by exactly one visible bar, in both directions, with no skipped bar and no press that
     * changes nothing.
     */
    private fun stepCombined(direction: Int) {
        val s = coordinator.uiState()
        if (s.muted) { coordinator.toggleMute(); return }
        if (s.zoneQuiet) {
            val next = currentStep + direction
            when {
                next in 0..QUIET_TOP_VISIBLE -> selectQuiet(next)
                // Up from −5 dB: cross the line onto the last upper rung, which IS the floor.
                next > QUIET_TOP_VISIBLE && s.upperCount >= 1 ->
                    coordinator.applyUpper(s.upperCount - 1)
                // Down from −30: nothing (true silence is the mute button's job only).
            }
        } else {
            val next = s.upperPos - direction   // pos 0 = loudest, so "up" lowers pos
            when {
                next in 0 until s.upperCount -> coordinator.applyUpper(next)
                // Down past the floor rung: the first level genuinely below the minimum.
                next >= s.upperCount -> selectQuiet(QUIET_TOP_VISIBLE)
                // Up past rung 0: already at max, nothing.
            }
        }
    }

    private fun selectQuiet(step: Int) {
        // Commit the local highlight only when the step can actually be applied. During a
        // cellular call applyQuiet refuses (and says why), so moving currentStep first would
        // leave the chevron stepping from a position the dial never reached.
        // The same holds for a locked range: applyQuiet refuses and opens the paywall, and
        // the coordinator's own contract is that the dial never renders a step the device
        // is not applying. Until 2026-09-09 a refused tap still advanced currentStep here;
        // invisible today only because render() ignores currentStep outside the quiet zone.
        if (!coordinator.uiState().quietUnavailable && !coordinator.lockedProvider()) currentStep = step
        coordinator.applyQuiet(STEP_DB[step])
    }

    private fun dismissTooltipIfShown(root: View) {
        val tip = root.findViewById<TextView>(R.id.gv_line_tooltip) ?: return
        if (tip.visibility == View.VISIBLE) {
            tip.visibility = View.GONE
            Prefs.setLineTooltipShown(context)
        }
    }

    private fun hit(v: View, rawX: Float, rawY: Float, slop: Int = 0): Boolean {
        if (v.visibility != View.VISIBLE) return false
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return rawX >= loc[0] - slop && rawX <= loc[0] + v.width + slop &&
               rawY >= loc[1] - slop && rawY <= loc[1] + v.height + slop
    }

    private fun flash(v: View) {
        v.animate().alpha(0.4f).setDuration(60L).withEndAction {
            v.animate().alpha(1f).setDuration(120L).start()
        }.start()
    }

    // ────────────────────────────────────────────────────────────────
    // Rendering — one function, driven by the coordinator's snapshot
    // ────────────────────────────────────────────────────────────────

    private fun render(view: View) {
        val s = coordinator.uiState()
        buildUpperBars(view)   // no-op unless the rung count changed (mode/route flip)

        val quietBars = collectStepBars(view)
        val label = view.findViewById<TextView>(R.id.gv_label_db)
        val btnMute = view.findViewById<View>(R.id.gv_btn_mute)
        val muteGlyph = view.findViewById<ImageView>(R.id.gv_mute_glyph)
        val muteText = view.findViewById<TextView>(R.id.gv_mute_text)

        // Never let the hidden floor step become the selection; -5 dB is the visible top.
        if (s.zoneQuiet) currentStep = dbToStep(s.quietDb).coerceAtMost(QUIET_TOP_VISIBLE)

        // A locked range (trial over, no key) renders every bar at the "unavailable" level
        // the cellular-call state already uses, so the dial reads as inert instead of as a
        // live control whose every touch bounces to a sales sheet. The label is left alone on
        // purpose: it still shows where the user left off, which is what the keep-your-place
        // rule promises. Added 2026-09-09; before it a locked dial was pixel identical to a
        // working one.
        val locked = coordinator.lockedDisplayProvider()

        // Upper bars: list index 0 = loudest. Fill from the bottom up to the current level.
        for (i in upperBars.indices) {
            val alpha = when {
                locked                 -> ALPHA_UNAVAILABLE
                s.zoneQuiet || s.muted -> ALPHA_INACTIVE
                i == s.upperPos        -> ALPHA_CURRENT
                i > s.upperPos         -> ALPHA_ACTIVE
                else                   -> ALPHA_INACTIVE
            }
            upperBars[i].animate().alpha(alpha).setDuration(ANIM_MS).start()
        }

        // Quiet bars: exactly the 1.3.4 scheme.
        for (i in quietBars.indices) {
            val alpha = when {
                // Cellular call: the gain cannot reach the telephony output, so every quiet
                // bar is inert. Render them below the ordinary inactive level so the zone
                // reads as unavailable rather than merely unselected, and the user is not
                // invited to tap something that can only disappoint.
                locked                  -> ALPHA_UNAVAILABLE
                s.quietUnavailable      -> ALPHA_UNAVAILABLE
                !s.zoneQuiet || s.muted -> ALPHA_INACTIVE
                i == currentStep        -> ALPHA_CURRENT
                i < currentStep         -> ALPHA_ACTIVE
                else                    -> ALPHA_INACTIVE
            }
            quietBars[i].animate().alpha(alpha).setDuration(ANIM_MS).start()
        }

        // Label: % above the line, dB below, MUTE while muted (locked label spec).
        // 1.4.4: the label also mirrors the muted state in COLOUR, so mute is carried by
        // three redundant channels (bar fill, glyph, label) — never by colour alone.
        label.text = when {
            s.muted     -> context.getString(R.string.gv_label_muted)
            s.zoneQuiet -> formatDb(STEP_DB[currentStep])
            else        -> "${s.percent}%"
        }
        label.setTextColor(if (s.muted) COLOR_MUTED_ACCENT else COLOR_LABEL_NORMAL)

        // 4.1.3: the level is the app's whole output, and until 1.5.0 a screen reader was told
        // nothing when it moved. The label is a polite live region carrying a SPOKEN form of the
        // value ("Volume minus 15 dB, below the device minimum"), not the terse visible one.
        // Guarded on the previous value so a re-render with the same level stays silent, and so a
        // drag does not queue one announcement per frame.
        val spoken = when {
            s.muted     -> context.getString(R.string.gv_label_desc_muted)
            s.zoneQuiet -> context.getString(R.string.gv_label_desc_quiet, formatDb(STEP_DB[currentStep]))
            else        -> context.getString(R.string.gv_label_desc_normal, s.percent)
        }
        label.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        if (spoken != lastSpokenLevel) {
            lastSpokenLevel = spoken
            label.contentDescription = spoken
        }

        // 1.4.4 option B mute bar: state_selected drives the red fill in bg_mute_bar;
        // glyph and text invert onto it. Announced to TalkBack as a STATE via the
        // container's contentDescription, not as new text.
        btnMute.isSelected = s.muted
        muteGlyph.setColorFilter(if (s.muted) COLOR_MUTE_INVERTED else COLOR_MUTE_CONTENT)
        muteText.setTextColor(if (s.muted) COLOR_MUTE_INVERTED else COLOR_MUTE_CONTENT)
        muteText.text = context.getString(
            if (s.muted) R.string.gv_mute_bar_label_active else R.string.gv_mute_bar_label
        )
        btnMute.contentDescription = context.getString(
            if (s.muted) R.string.gv_label_muted else R.string.gv_mute
        )
    }

    // ────────────────────────────────────────────────────────────────
    // 1.4.4: press feedback, haptics, key-press flash
    // ────────────────────────────────────────────────────────────────

    /**
     * Animations globally disabled by the user (the platform's reduced-motion signal):
     * every animated affordance falls back to an instant, non-animated equivalent.
     */
    private fun animationsEnabled(): Boolean = try {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) != 0f
    } catch (e: Exception) {
        true
    }

    /**
     * Option B pressed feedback for the glass keys and the mute bar. The system never sets
     * state_pressed itself (children are non-clickable under the unified touch handler),
     * so it is driven here: pressed drawable + scale 0.96, released after PRESS_MS.
     */
    private fun pressPulse(v: View) {
        v.isPressed = true
        if (animationsEnabled()) {
            v.animate().scaleX(PRESS_SCALE).scaleY(PRESS_SCALE).setDuration(PRESS_MS / 2)
                .withEndAction {
                    v.isPressed = false
                    v.animate().scaleX(1f).scaleY(1f).setDuration(PRESS_MS).start()
                }.start()
        } else {
            v.postDelayed({ v.isPressed = false }, PRESS_MS)
        }
    }

    private fun tick(root: View) {
        root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun confirmHaptic(root: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        root.performHapticFeedback(constant)
    }

    /**
     * A physical key press changed the hardware index but not the highlighted bar — flash
     * the bar the coordinator named, so the press never reads as ignored.
     * QUIET_FIRST (the owner-approved boundary case) pulses TWICE on the first bar below
     * the orange line: an invitation downward, not a wall.
     * Runs FLASH_DELAY_MS after the render pass so the two never fight one View's animator.
     */
    private fun onFlash(view: View, target: FullRangeCoordinator.FlashTarget) {
        view.postDelayed({
            if (overlayView == null) return@postDelayed
            val s = coordinator.uiState()
            val bar: View? = when (target) {
                FullRangeCoordinator.FlashTarget.UPPER_CURRENT ->
                    upperBars.getOrNull(s.upperPos)
                FullRangeCoordinator.FlashTarget.QUIET_CURRENT ->
                    view.findViewById(quietBarId(currentStep))
                FullRangeCoordinator.FlashTarget.QUIET_FIRST ->
                    view.findViewById(R.id.gv_step_bar_5)
            }
            bar ?: return@postDelayed
            val pulses = if (target == FullRangeCoordinator.FlashTarget.QUIET_FIRST) 2 else 1
            pulseBar(view, bar, pulses)
        }, FLASH_DELAY_MS)
    }

    /**
     * The key just arrived. Called by the service ONCE per purchase, after it has put the
     * audio where it belongs.
     *
     * The dial wakes from its idle fade, confirms with one haptic, and runs one wave of light up
     * the WHOLE ladder, bottom to top, then hands every bar back to render(), which settles on the
     * step now selected. This is legibility, not decoration: until 2026-09-10 a buyer who had
     * just paid was looking at the same dim, inert ladder the lock had drawn, and the only way to
     * learn that the purchase worked was to touch it. Found on the owner's own phone.
     *
     * The whole ladder, not "up to the current step", because the first version did exactly that
     * and the screenshots showed it: for a buyer landing on -30 dB, the deepest step and the most
     * natural place for this app's audience, the sweep was ONE bar. The message of the moment is
     * "the full range is open", so the wave covers all of it, whatever the position.
     *
     * Muted: no sweep, because a ladder that lights up and settles to all-inactive reads as a
     * glitch. Reduced motion: truth, wake and haptic only.
     */
    fun celebrateUnlock() {
        // A purchase is the one moment the dial should be in view whatever the user did with
        // it: the wave up the ladder is the confirmation, and a blade cannot show one.
        // 1.5.3: expand() is animated, so the dial is not on screen yet at this instant. Until
        // then the wave was skipped because overlayView was still null. Now it waits for the
        // dial: expand() plays it at the end of its entrance (and a collapse in flight turns
        // straight back).
        if (bladeRoot != null || morphing) {
            pendingCelebration = true
            if (!morphing) expand()
            return
        }
        val view = overlayView ?: return
        android.util.Log.d("GranularVolume", "unlock celebrated on the dial")
        render(view)
        wake(view)
        confirmHaptic(view)
        val s = coordinator.uiState()
        if (s.muted || !animationsEnabled()) { scheduleIdleFade(view); return }

        // Bottom to top: quiet steps from -30 dB up to the last visible one, then the upper rungs
        // from the floor rung up to the loudest (upper list index 0 is the loudest).
        val quiet = collectStepBars(view)
        val ladder = ArrayList<View>()
        for (i in 0..QUIET_TOP_VISIBLE) ladder.add(quiet[i])
        for (i in upperBars.lastIndex downTo 0) ladder.add(upperBars[i])
        // Each bar rises, holds, and falls back, so the light travels. postDelayed, never
        // setStartDelay: a ViewPropertyAnimator keeps its start delay for every later animate() on
        // that view, so render()'s own animations would inherit it.
        ladder.forEachIndexed { k, bar ->
            view.postDelayed({
                if (overlayView !== view) return@postDelayed
                bar.animate().alpha(ALPHA_CURRENT).setDuration(FLASH_IN_MS).withEndAction {
                    bar.postDelayed({
                        if (overlayView === view) bar.animate().alpha(ALPHA_INACTIVE).setDuration(FLASH_OUT_MS).start()
                    }, FLASH_HOLD_MS)
                }.start()
            }, FLASH_DELAY_MS + k * SWEEP_STAGGER_MS)
        }
        val settleAt = FLASH_DELAY_MS + ladder.size * SWEEP_STAGGER_MS + FLASH_IN_MS + FLASH_HOLD_MS + FLASH_OUT_MS
        view.postDelayed({
            if (overlayView === view) { render(view); scheduleIdleFade(view) }
        }, settleAt)
    }

    private fun pulseBar(root: View, bar: View, pulses: Int) {
        if (!animationsEnabled()) {
            // Reduced motion: one instant blink, no interpolation.
            bar.alpha = ALPHA_CURRENT
            bar.postDelayed({ overlayView?.let { render(it) } }, FLASH_IN_MS + FLASH_HOLD_MS + FLASH_OUT_MS)
            return
        }
        bar.animate().alpha(ALPHA_CURRENT).setDuration(FLASH_IN_MS).withEndAction {
            bar.postDelayed({
                if (pulses > 1) {
                    bar.animate().alpha(ALPHA_INACTIVE).setDuration(FLASH_OUT_MS).withEndAction {
                        pulseBar(root, bar, pulses - 1)
                    }.start()
                } else {
                    // Hand the bar back to the render pipeline's truth.
                    overlayView?.let { render(it) }
                }
            }, FLASH_HOLD_MS)
        }.start()
    }

    private fun quietBarId(step: Int): Int = when (step) {
        0 -> R.id.gv_step_bar_0
        1 -> R.id.gv_step_bar_1
        2 -> R.id.gv_step_bar_2
        3 -> R.id.gv_step_bar_3
        4 -> R.id.gv_step_bar_4
        5 -> R.id.gv_step_bar_5
        else -> R.id.gv_step_bar_6
    }

    private fun dbToStep(dB: Float): Int =
        STEP_DB.indices.minByOrNull { abs(STEP_DB[it] - dB) } ?: STEP_DB.lastIndex

    private fun formatDb(dB: Float) =
        if (dB == 0f) "0 dB" else "%.0f dB".format(dB)

    private fun collectStepBars(view: View): Array<View> = arrayOf(
        view.findViewById(R.id.gv_step_bar_0),
        view.findViewById(R.id.gv_step_bar_1),
        view.findViewById(R.id.gv_step_bar_2),
        view.findViewById(R.id.gv_step_bar_3),
        view.findViewById(R.id.gv_step_bar_4),
        view.findViewById(R.id.gv_step_bar_5),
        view.findViewById(R.id.gv_step_bar_6)
    )

    // ────────────────────────────────────────────────────────────────
    // Bounds — the "hide it, but never onto the Home keys" behaviour (unchanged)
    // ────────────────────────────────────────────────────────────────

    private fun clampToBounds(view: View): Boolean {
        val w = view.width
        val h = view.height
        if (w == 0 || h == 0) return false

        val screen = fullDisplayBounds()
        val navTop = screen.height() - navBarHeight()
        val visibleAtBottom = h / BOTTOM_VISIBLE_FRACTION

        // 1.5.1 pilot 2: the dial stays fully on screen sideways. Parking it half off the
        // edge was the old way to get it out of the way; the docked tab is the new one, and
        // a wall the dial can be flush against is what the suction glides it to.
        val minX = 0
        val maxX = max(minX, screen.width() - w)
        val minY = statusBarHeight()
        val maxY = max(minY, navTop - visibleAtBottom)
        // Remembered for the push-through test only; the clamp itself is unchanged.
        lastMinX = minX
        lastMaxX = maxX

        val newX = layoutParams.x.coerceIn(minX, maxX)
        val newY = layoutParams.y.coerceIn(minY, maxY)
        val changed = newX != layoutParams.x || newY != layoutParams.y
        layoutParams.x = newX
        layoutParams.y = newY
        return changed
    }

    private fun fullDisplayBounds(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Rect(wm.currentWindowMetrics.bounds)
        }
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        return Rect(0, 0, dm.widthPixels, dm.heightPixels)
    }

    private fun statusBarHeight(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val top = wm.currentWindowMetrics.windowInsets
                .getInsets(WindowInsets.Type.statusBars()).top
            if (top > 0) return top
        }
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    private fun navBarHeight(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bottom = wm.currentWindowMetrics.windowInsets
                .getInsets(WindowInsets.Type.navigationBars()).bottom
            if (bottom > 0) return bottom
        }
        val id = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    private fun applyLayout() {
        overlayView?.let { runCatching { wm.updateViewLayout(it, layoutParams) } }
    }

    private fun savePosition() {
        Prefs.setOverlayPosition(context, layoutParams.x, layoutParams.y)
    }

    private fun wake(root: View) {
        root.removeCallbacks(idleFadeRunnable)
        root.animate().alpha(ACTIVE_ALPHA).setDuration(WAKE_MS).start()
    }

    private fun scheduleIdleFade(root: View) {
        root.removeCallbacks(idleFadeRunnable)
        if (tourView != null) return
        root.postDelayed(idleFadeRunnable, IDLE_FADE_DELAY_MS)
    }
}

/**
 * The tour's highlight ring, drawn INSIDE the dial through its ViewOverlay: a 2dp accent
 * outline with a soft glow around the element being described. Bounds are in the dial's
 * own coordinates and are animated between steps by [OverlayManager].
 */
private class TourRingDrawable(density: Float) : Drawable() {
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 8f * density; color = 0x3A8179FF
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * density; color = 0xFF8179FF.toInt()
    }
    private val radius = 10f * density
    private val rectF = RectF()
    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        rectF.set(bounds)
        canvas.drawRoundRect(rectF, radius, radius, glow)
        canvas.drawRoundRect(rectF, radius, radius, ring)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
