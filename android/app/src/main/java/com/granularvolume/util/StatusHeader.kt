package com.granularvolume.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.granularvolume.R

/**
 * The headline row of the access sheets (1.6.0, owner's request): a status badge beside the
 * headline, so the state reads at a glance before a word is read.
 *
 *   LOCKED  closed padlock, amber          the dial is locked
 *   OPEN    open padlock with a check, green   bought, key owner, grandfathered, F-Droid
 *   TRIAL   clock, accent                  the free week is running
 *
 * Colours are the app's existing status pair (the permission pills on the main screen use the
 * same success and warning tokens), so the sheet speaks the language the app already speaks.
 * The badge is decorative for screen readers: the headline beside it already says the state,
 * and announcing "locked" twice would be noise.
 */
object StatusHeader {

    enum class Kind { LOCKED, OPEN, TRIAL }

    fun build(context: Context, headline: TextView, kind: Kind): View {
        val (icon, fg, bg) = when (kind) {
            Kind.LOCKED -> Triple(R.drawable.ic_lock_closed, R.color.gv_warning, R.color.gv_warning_dim)
            Kind.OPEN -> Triple(R.drawable.ic_lock_open_check, R.color.gv_success, R.color.gv_success_dim)
            Kind.TRIAL -> Triple(R.drawable.ic_clock, R.color.gv_accent_text, R.color.gv_accent_dim)
        }
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val badge = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(ContextCompat.getColor(context, bg))
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(
                ImageView(context).apply {
                    setImageResource(icon)
                    imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, fg))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER)
            )
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(badge, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(
                headline,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(14)
                }
            )
        }
    }
}
