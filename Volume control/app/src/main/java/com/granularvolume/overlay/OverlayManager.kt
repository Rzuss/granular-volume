package com.granularvolume.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import com.granularvolume.R
import com.granularvolume.audio.AudioController
import com.granularvolume.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Floating overlay: 7 discrete dB step bars + up/down chevrons + dB label + close.
 *
 * Touch architecture:
 *  - A SINGLE unified touch handler on the root makes the ENTIRE pill draggable from anywhere,
 *    while short taps select a step / step the chevrons / close.
 *  - Child views are made non-clickable so every touch reaches the root handler; tap targets
 *    are resolved by on-screen hit-testing.
 *  - FLAG_NOT_TOUCH_MODAL keeps touches OUTSIDE the pill flowing to the app below, so the
 *    control never interferes with a full-screen app (e.g. YouTube).
 *
 * Hiding (drag the pill off an edge — the old, simple behaviour, but bounded):
 *  - DOWN: the pill can be pushed down until only its top quarter (the close button + handle)
 *    stays visible ABOVE the navigation bar. The rest slides off below. The close button never
 *    descends onto the Home / Back keys, so it always stays tappable and the keys keep working.
 *  - LEFT / RIGHT: the pill can be tucked off a side edge, leaving a grabbable sliver.
 *  - TOP: never past the status bar, so the close button is always reachable.
 *
 *  - After a few idle seconds the pill dims so it stops distracting, and wakes on touch.
 *  - UI updates always run on the main thread (flow collected on Dispatchers.Main).
 */
class OverlayManager(
    private val context: Context,
    private val audioController: AudioController,
    private val scope: CoroutineScope,
    private val onDismiss: () -> Unit
) {

    companion object {
        // Step index 0 = quietest (−30 dB), index 6 = no attenuation (0 dB).
        val STEP_DB = floatArrayOf(-30f, -25f, -20f, -15f, -10f, -5f, 0f)

        private const val DEFAULT_X = 24
        private const val DEFAULT_Y = 200
        private const val DRAG_SLOP_PX = 12
        private const val ANIM_MS = 120L
        // Sliver kept on-screen when the pill is tucked off a SIDE edge (so it can be grabbed).
        private const val SIDE_PEEK_DP = 50
        // When pushed to the bottom, this fraction of the pill stays visible above the nav bar.
        // The rest hides below. 4 → a quarter remains (the close button + handle).
        private const val BOTTOM_VISIBLE_FRACTION = 4
        // Idle dimming.
        private const val IDLE_FADE_DELAY_MS = 3500L
        private const val IDLE_FADE_MS = 380L
        private const val WAKE_MS = 120L
        private const val IDLE_ALPHA = 0.4f
        private const val ACTIVE_ALPHA = 1.0f

        private const val ALPHA_CURRENT  = 1.00f
        private const val ALPHA_ACTIVE   = 0.50f
        private const val ALPHA_INACTIVE = 0.10f
    }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density
    // The close button is intentionally small; expand its tap area so it stays easy to hit.
    private val dismissHitSlop = (12 * density).toInt()
    private var overlayView: View? = null
    private var flowJob: Job? = null
    private var currentStep = STEP_DB.size - 1   // Start at 0 dB

    /** Dims the pill once it has sat idle, so it stops competing for attention. */
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
        // applicationContext has no theme — ContextThemeWrapper supplies ?attr/* resolution.
        val themedCtx = ContextThemeWrapper(context, R.style.Theme_GranularVolume)
        val view = LayoutInflater.from(themedCtx).inflate(R.layout.overlay_slider, null)
        overlayView = view
        setupView(view)
        wm.addView(view, layoutParams)

        // Once measured, pull the saved position back inside the allowed bounds.
        view.post {
            if (clampToBounds(view)) applyLayout()
            scheduleIdleFade(view)
        }

        // Observe attenuation changes ON THE MAIN THREAD (views must not be touched off-thread).
        flowJob = scope.launch(Dispatchers.Main) {
            audioController.attenuationDb.collect { dB -> updateFromDb(view, dB) }
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
        val stepBars   = collectStepBars(view)
        val labelDb    = view.findViewById<TextView>(R.id.gv_label_db)
        val btnUp      = view.findViewById<ImageButton>(R.id.gv_btn_up)
        val btnDown    = view.findViewById<ImageButton>(R.id.gv_btn_down)
        val btnDismiss = view.findViewById<ImageButton>(R.id.gv_btn_dismiss)

        // Disable child click handling so EVERY touch reaches the root unified handler.
        // This is what lets the whole pill be dragged from anywhere.
        for (b in stepBars) { b.isClickable = false; b.isFocusable = false }
        btnUp.isClickable = false
        btnDown.isClickable = false
        btnDismiss.isClickable = false

        setupUnifiedTouch(view, stepBars, labelDb, btnUp, btnDown, btnDismiss)
        updateStepUI(stepBars, labelDb, currentStep)
    }

    /**
     * One listener to rule them all: drag the entire pill, or — if the finger barely
     * moved — treat it as a tap and route it to whatever control sits under the finger.
     */
    private fun setupUnifiedTouch(
        root: View,
        bars: Array<View>,
        label: TextView,
        btnUp: View,
        btnDown: View,
        btnDismiss: ImageButton
    ) {
        var initialX = 0
        var initialY = 0
        var downRawX = 0f
        var downRawY = 0f
        var dragging = false

        root.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    wake(root)                       // restore full opacity immediately
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
                        handleTap(e.rawX, e.rawY, bars, label, btnUp, btnDown, btnDismiss)
                    }
                    scheduleIdleFade(root)
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun handleTap(
        rawX: Float,
        rawY: Float,
        bars: Array<View>,
        label: TextView,
        btnUp: View,
        btnDown: View,
        btnDismiss: ImageButton
    ) {
        if (hit(btnDismiss, rawX, rawY, dismissHitSlop)) {
            flash(btnDismiss); onDismiss(); return
        }
        if (hit(btnUp, rawX, rawY)) {
            flash(btnUp); setStep(min(currentStep + 1, STEP_DB.lastIndex), bars, label); return
        }
        if (hit(btnDown, rawX, rawY)) {
            flash(btnDown); setStep(max(currentStep - 1, 0), bars, label); return
        }
        for (i in bars.indices) {
            if (hit(bars[i], rawX, rawY)) { setStep(i, bars, label); return }
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
    // Bounds — the "hide it, but never onto the Home keys" behaviour
    // ────────────────────────────────────────────────────────────────

    /**
     * Coerce [layoutParams] into the allowed range. Returns true if the position changed.
     *
     *  - X: may tuck off a side edge, keeping [SIDE_PEEK_DP] on-screen.
     *  - Y top: never above the status bar.
     *  - Y bottom: may push down until only the top 1/[BOTTOM_VISIBLE_FRACTION] of the pill is
     *    left above the navigation bar — so the close button stays above the Home keys.
     */
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
        // Lowest top-position: the pill's visible portion above the nav bar shrinks to a quarter.
        val maxY = max(minY, navTop - visibleAtBottom)

        val newX = layoutParams.x.coerceIn(minX, maxX)
        val newY = layoutParams.y.coerceIn(minY, maxY)
        val changed = newX != layoutParams.x || newY != layoutParams.y
        layoutParams.x = newX
        layoutParams.y = newY
        return changed
    }

    /** Full physical display rectangle in pixels (includes system bars). */
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

    /** Height of the bottom navigation / gesture bar, so the pill never overlaps the Home keys. */
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

    /** Restore full opacity and hold off the idle dim while the user is interacting. */
    private fun wake(root: View) {
        root.removeCallbacks(idleFadeRunnable)
        root.animate().alpha(ACTIVE_ALPHA).setDuration(WAKE_MS).start()
    }

    private fun scheduleIdleFade(root: View) {
        root.removeCallbacks(idleFadeRunnable)
        root.postDelayed(idleFadeRunnable, IDLE_FADE_DELAY_MS)
    }

    // ────────────────────────────────────────────────────────────────
    // State
    // ────────────────────────────────────────────────────────────────

    private fun setStep(step: Int, bars: Array<View>, label: TextView) {
        currentStep = step
        audioController.setAttenuation(STEP_DB[step])
        updateStepUI(bars, label, step)
    }

    private fun updateStepUI(bars: Array<View>, label: TextView, step: Int) {
        for (i in bars.indices) {
            val alpha = when {
                i == step -> ALPHA_CURRENT
                i < step  -> ALPHA_ACTIVE
                else      -> ALPHA_INACTIVE
            }
            bars[i].animate().alpha(alpha).setDuration(ANIM_MS).start()
        }
        label.text = formatDb(STEP_DB[step])
    }

    private fun updateFromDb(view: View, dB: Float) {
        val step = dbToStep(dB)
        if (step == currentStep) return
        currentStep = step
        updateStepUI(collectStepBars(view), view.findViewById(R.id.gv_label_db), step)
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
}
