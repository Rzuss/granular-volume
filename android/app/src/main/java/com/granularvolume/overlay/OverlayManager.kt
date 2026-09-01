package com.granularvolume.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
    private val onDismiss: () -> Unit
) {

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
        private const val SIDE_PEEK_DP = 50
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
        private const val MUTE_SLOP_DP = 12

        // 1.4.4 press feedback + key-press flash.
        private const val PRESS_SCALE = 0.96f
        private const val PRESS_MS = 120L
        // Flash starts after render()'s ANIM_MS alpha animations settle, so the two never
        // fight over the same View's animator.
        private const val FLASH_DELAY_MS = 140L
        private const val FLASH_IN_MS = 90L
        private const val FLASH_HOLD_MS = 60L
        private const val FLASH_OUT_MS = 220L

        private const val COLOR_LABEL_NORMAL = 0x88FFFFFF.toInt()
        private const val COLOR_MUTED_ACCENT = 0xFFFF5A5F.toInt()
        private const val COLOR_MUTE_CONTENT = 0x99FFFFFF.toInt()
        private const val COLOR_MUTE_INVERTED = 0xFF1A1A2E.toInt()
    }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density
    private val dismissHitSlop = (12 * density).toInt()
    private val chevronHitSlop = (CHEVRON_SLOP_DP * density).toInt()
    private val muteHitSlop = (MUTE_SLOP_DP * density).toInt()
    private var overlayView: View? = null
    private var flowJob: Job? = null

    /** Quiet-zone step currently selected (meaningful only while zoneQuiet). */
    private var currentStep = STEP_DB.size - 1

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

    /** Inflate and attach the overlay. Throws on failure so the caller can report it. */
    fun show() {
        if (overlayView != null) return
        val themedCtx = ContextThemeWrapper(context, R.style.Theme_GranularVolume)
        val view = LayoutInflater.from(themedCtx).inflate(R.layout.overlay_slider, null)
        overlayView = view
        setupView(view)
        wm.addView(view, layoutParams)

        view.post {
            if (clampToBounds(view)) applyLayout()
            scheduleIdleFade(view)
        }

        // One render pipeline: any state change (gain flow or coordinator revision) re-renders.
        flowJob = scope.launch(Dispatchers.Main) {
            launch { audioController.attenuationDb.collect { render(view) } }
            launch { coordinator.uiRevision.collect { render(view) } }
            // 1.4.4: key-press flash acknowledgements (id 0 is the initial empty signal).
            launch {
                coordinator.flash.collect { sig ->
                    if (sig.id > 0 && sig.target != null) onFlash(view, sig.target)
                }
            }
        }
    }

    fun hide() {
        flowJob?.cancel()
        flowJob = null
        overlayView?.let {
            it.removeCallbacks(idleFadeRunnable)
            runCatching { wm.removeView(it) }
            overlayView = null
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Setup
    // ────────────────────────────────────────────────────────────────

    private fun setupView(view: View) {
        val quietBars  = collectStepBars(view)
        val btnUp      = view.findViewById<ImageButton>(R.id.gv_btn_up)
        val btnDown    = view.findViewById<ImageButton>(R.id.gv_btn_down)
        val btnDismiss = view.findViewById<ImageButton>(R.id.gv_btn_dismiss)
        // 1.4.4: the mute control is a full-width bar (LinearLayout), no longer an ImageButton.
        val btnMute    = view.findViewById<View>(R.id.gv_btn_mute)

        buildUpperBars(view)

        // Disable child click handling so EVERY touch reaches the root unified handler.
        for (b in quietBars) { b.isClickable = false; b.isFocusable = false }
        btnUp.isClickable = false
        btnDown.isClickable = false
        btnDismiss.isClickable = false
        btnMute.isClickable = false

        // One-time hint next to the line (full-range onboarding spec: this is ALL of it).
        if (!Prefs.wasLineTooltipShown(context)) {
            view.findViewById<TextView>(R.id.gv_line_tooltip).visibility = View.VISIBLE
        }

        setupUnifiedTouch(view, quietBars, btnUp, btnDown, btnDismiss, btnMute)
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
        if (count == upperBars.size) return
        container.removeAllViews()
        upperBars.clear()

        val gapPx = (UPPER_BAR_GAP_DP * density).toInt()
        val totalPx = (UPPER_CONTAINER_DP * density).toInt()
        val barPx = max(
            (UPPER_BAR_MIN_DP * density).toInt(),
            (totalPx - gapPx * (count - 1)) / count
        )

        for (i in 0 until count) {
            val bar = View(container.context).apply {
                background = container.context.getDrawable(R.drawable.bg_step_bar)
                contentDescription = container.context.getString(R.string.gv_upper_bar_desc)
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
        btnMute: View
    ) {
        var initialX = 0
        var initialY = 0
        var downRawX = 0f
        var downRawY = 0f
        var dragging = false

        root.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    wake(root)
                    dismissTooltipIfShown(root)
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    downRawX = e.rawX
                    downRawY = e.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downRawX).toInt()
                    val dy = (e.rawY - downRawY).toInt()
                    if (!dragging && (abs(dx) > DRAG_SLOP_PX || abs(dy) > DRAG_SLOP_PX)) {
                        dragging = true
                    }
                    if (dragging) {
                        layoutParams.x = initialX + dx
                        layoutParams.y = initialY + dy
                        clampToBounds(root)
                        applyLayout()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        clampToBounds(root)
                        applyLayout()
                        savePosition()
                    } else {
                        handleTap(root, e.rawX, e.rawY, quietBars, btnUp, btnDown, btnDismiss, btnMute)
                    }
                    scheduleIdleFade(root)
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
        btnMute: View
    ) {
        if (hit(btnDismiss, rawX, rawY, dismissHitSlop)) {
            flash(btnDismiss); onDismiss(); return
        }
        if (hit(btnUp, rawX, rawY, chevronHitSlop)) {
            pressPulse(btnUp); tick(root); stepCombined(+1); return
        }
        if (hit(btnDown, rawX, rawY, chevronHitSlop)) {
            pressPulse(btnDown); tick(root); stepCombined(-1); return
        }
        if (hit(btnMute, rawX, rawY, muteHitSlop)) {
            pressPulse(btnMute); confirmHaptic(root); coordinator.toggleMute(); return
        }
        for (i in upperBars.indices) {
            if (hit(upperBars[i], rawX, rawY)) { tick(root); coordinator.applyUpper(i); return }
        }
        for (i in quietBars.indices) {
            if (hit(quietBars[i], rawX, rawY)) { tick(root); selectQuiet(i); return }
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
        if (!coordinator.uiState().quietUnavailable) currentStep = step
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

        // Upper bars: list index 0 = loudest. Fill from the bottom up to the current level.
        for (i in upperBars.indices) {
            val alpha = when {
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
                // Cellular call: the gain cannot reach the telephony output, so every
                // quiet bar is inert. Render them below the ordinary inactive level so the
                // zone reads as unavailable rather than merely unselected, and the user is
                // not invited to tap something that can only disappoint.
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
        val sidePeek = (SIDE_PEEK_DP * density).toInt()
        val navTop = screen.height() - navBarHeight()
        val visibleAtBottom = h / BOTTOM_VISIBLE_FRACTION

        val minX = -(w - sidePeek)
        val maxX = max(minX, screen.width() - sidePeek)
        val minY = statusBarHeight()
        val maxY = max(minY, navTop - visibleAtBottom)

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
        root.postDelayed(idleFadeRunnable, IDLE_FADE_DELAY_MS)
    }
}
