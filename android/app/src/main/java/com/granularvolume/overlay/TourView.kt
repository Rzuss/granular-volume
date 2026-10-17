package com.granularvolume.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.granularvolume.R
import kotlin.math.max
import kotlin.math.min

/**
 * The feature tour (1.5.1): a full-screen scrim under the LIVE dial, a callout line from the
 * dial's edge to a card, and the card itself. The dial window sits above this one, so it
 * stays bright and touchable: the user can try each feature while reading about it, and the
 * dial is its own spotlight. The element being described is ringed inside the dial by
 * [OverlayManager] (a ViewOverlay drawable), because nothing drawn here can show through
 * the pill.
 *
 * Geometry is in screen coordinates: the caller passes the target's on-screen rect and the
 * dial's on-screen rect, and this view subtracts its own screen offset.
 */
class TourView(
    context: Context,
    private val onSkip: () -> Unit,
    private val onNext: () -> Unit
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density
    private fun dpi(v: Float): Int = (v * density).toInt()

    private val scrimPaint = Paint().apply { color = 0xA6000000.toInt() }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        strokeCap = Paint.Cap.ROUND
        color = 0xC0FFFFFF.toInt()
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = 0x66FFFFFF
    }

    private val linePath = Path()
    private val partialPath = Path()
    private val measure = PathMeasure()
    private var lineProgress = 0f
    private var lineAnimator: ValueAnimator? = null
    private var startX = 0f
    private var startY = 0f
    private var hasLine = false

    // Card
    private val card: LinearLayout
    private val title: TextView
    private val body: TextView
    private val counter: TextView
    private val dots: LinearLayout
    private val skip: TextView
    private val next: TextView
    private val cardWidth = dpi(296f)

    init {
        setWillNotDraw(false)
        val pad = dpi(18f)
        card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, dpi(14f))
            background = GradientDrawable().apply {
                cornerRadius = dp(18f)
                setColor(0xF716203A.toInt())
                setStroke(dpi(1f), ContextCompat.getColor(context, R.color.gv_surface_stroke))
            }
            elevation = dp(12f)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        counter = text(11f, R.color.gv_text_muted).apply { letterSpacing = 0.08f }
        title = text(16f, R.color.gv_text_primary, bold = true)
        body = text(13.5f, R.color.gv_text_secondary).apply { setLineSpacing(dp(3f), 1f) }
        dots = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        skip = text(13f, R.color.gv_text_muted, bold = true).apply {
            text = context.getString(R.string.gv_tour_skip)
            setPadding(dpi(10f), dpi(8f), dpi(10f), dpi(8f))
            setOnClickListener { onSkip() }
        }
        next = text(13f, R.color.gv_on_accent, bold = true).apply {
            setPadding(dpi(18f), dpi(9f), dpi(18f), dpi(9f))
            background = GradientDrawable().apply {
                cornerRadius = dp(20f)
                setColor(ContextCompat.getColor(context, R.color.gv_accent))
            }
            setOnClickListener { onNext() }
        }
        card.addView(counter)
        card.addView(title, lp(topDp = 4f))
        card.addView(body, lp(topDp = 6f))
        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(dots, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(skip)
            addView(next, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dpi(6f) })
        }
        card.addView(footer, lp(topDp = 14f))
        addView(card, LayoutParams(cardWidth, LayoutParams.WRAP_CONTENT))
        card.alpha = 0f
    }

    private fun text(sizeSp: Float, colorRes: Int, bold: Boolean = false): TextView =
        TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(ContextCompat.getColor(context, colorRes))
            typeface = android.graphics.Typeface.create(
                if (bold) "sans-serif-medium" else "sans-serif", android.graphics.Typeface.NORMAL
            )
        }

    private fun lp(topDp: Float) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dpi(topDp) }

    /**
     * Show one step. [target] and [dial] are on-screen rects; [cardOnRight] says which side
     * of the dial the card goes on. The line leaves the dial's edge at the target's centre
     * height and curves into the card's near edge.
     */
    fun showStep(
        step: Int, count: Int, titleText: String, bodyText: String,
        target: Rect, dial: Rect, cardOnRight: Boolean, animate: Boolean
    ) {
        val loc = IntArray(2); getLocationOnScreen(loc)
        val ox = loc[0]; val oy = loc[1]
        val w = width; val h = height

        counter.text = context.getString(R.string.gv_tour_step, step, count)
        title.text = titleText
        body.text = bodyText
        next.text = context.getString(if (step == count) R.string.gv_tour_done else R.string.gv_tour_next)
        buildDots(step, count)

        // Card placement: beside the dial, vertically centred on the target, kept on screen.
        val margin = dpi(16f)
        val gap = dpi(36f)
        val availW = if (cardOnRight) w - (dial.right - ox) - gap - margin else (dial.left - ox) - gap - margin
        val cw = min(cardWidth, max(dpi(200f), availW))
        val cardLp = card.layoutParams as LayoutParams
        cardLp.width = cw
        val cardX = if (cardOnRight) dial.right - ox + gap else dial.left - ox - gap - cw
        card.measure(
            MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.AT_MOST)
        )
        val ch = card.measuredHeight
        val targetCy = target.centerY() - oy
        // 1.7.3: when the window is shorter than the card plus its margins (the largest text size,
        // a phone held sideways, or a window not laid out yet) the range is empty, and coerceIn
        // threw and took the service down (Play vitals on 1.7.2). Then the card goes as low as it
        // fits, never above the top edge.
        val topY = margin + dpi(24f)
        val bottomY = h - ch - margin - dpi(24f)
        val cardY = if (bottomY >= topY) (targetCy - ch / 2).coerceIn(topY, bottomY)
                    else max(0, min(topY, h - ch - margin))
        cardLp.leftMargin = cardX
        cardLp.topMargin = cardY
        card.layoutParams = cardLp

        // The callout: dot on the dial's edge, S-curve into the card's near edge.
        startX = (if (cardOnRight) dial.right else dial.left) - ox.toFloat()
        startY = targetCy.toFloat()
        val endX = if (cardOnRight) cardX.toFloat() else (cardX + cw).toFloat()
        val endY = if (ch > dp(40f)) (cardY + ch / 2f).coerceIn(cardY + dp(20f), cardY + ch - dp(20f))
                   else cardY + ch / 2f
        val midX = (startX + endX) / 2f
        linePath.reset()
        linePath.moveTo(startX, startY)
        linePath.cubicTo(midX, startY, midX, endY, endX, endY)
        hasLine = true

        // Testability: where the Next button will be, on screen, once laid out.
        card.post {
            val nl = IntArray(2); next.getLocationOnScreen(nl)
            android.util.Log.d("GranularVolume", "tour step $step next=[${nl[0]},${nl[1]},${nl[0] + next.width},${nl[1] + next.height}]")
        }
        if (animate) {
            card.animate().cancel()
            card.alpha = 0f
            card.translationY = dp(10f)
            card.animate().alpha(1f).translationY(0f).setDuration(220L)
                .setInterpolator(DecelerateInterpolator()).setStartDelay(80L).start()
            lineAnimator?.cancel()
            lineProgress = 0f
            lineAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 300L
                interpolator = DecelerateInterpolator(1.6f)
                addUpdateListener { lineProgress = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            card.alpha = 1f
            card.translationY = 0f
            lineProgress = 1f
            invalidate()
        }
    }

    private fun buildDots(step: Int, count: Int) {
        dots.removeAllViews()
        for (i in 1..count) {
            val active = i == step
            val size = dpi(if (active) 7f else 6f)
            val dot = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (active) ContextCompat.getColor(context, R.color.gv_accent_text)
                             else 0x33FFFFFF)
                }
            }
            dots.addView(dot, LinearLayout.LayoutParams(size, size).apply { marginEnd = dpi(7f) })
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        if (!hasLine) return
        measure.setPath(linePath, false)
        partialPath.reset()
        measure.getSegment(0f, measure.length * lineProgress, partialPath, true)
        canvas.drawPath(partialPath, linePaint)
        val r = dp(3.5f)
        canvas.drawCircle(startX, startY, r, dotPaint)
        canvas.drawCircle(startX, startY, r + dp(3f), dotRingPaint)
    }

    /**
     * A tap on the scrim, outside the card, ends the tour. The scrim is a full-screen window
     * above every app, so without this a person who wandered off to another app mid-tour
     * would find it dimmed and deaf until they found the card. One stray tap and it is gone.
     */
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
            val r = Rect(); card.getHitRect(r)
            if (!r.contains(event.x.toInt(), event.y.toInt())) onSkip()
        }
        return true
    }

    fun fadeOut(onEnd: () -> Unit) {
        lineAnimator?.cancel()
        animate().alpha(0f).setDuration(160L).withEndAction { onEnd() }.start()
    }
}
