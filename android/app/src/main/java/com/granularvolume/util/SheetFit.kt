package com.granularvolume.util

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 1.7.4: keeps the info sheet and the paywall clear of the navigation bar and a display cutout
 * when the device is held sideways.
 *
 * With three-button navigation in landscape the bar sits on the right. Material padded the sheet
 * on that side by the bar's width even though the sheet, capped at its maximum width and centred
 * on the full screen, never reached the bar. The padding showed the sheet's own default
 * background as a strip beside our content (white in the light theme, grey in the dark).
 * The style [com.granularvolume.R.style.ThemeOverlay_GranularVolume_Sheet] turns that padding
 * off and paints the sheet in our surface colour; this centres the sheet inside the safe area,
 * so on a narrow landscape screen it can neither reach under the bar nor sit off centre.
 */
object SheetFit {

    /** Call after [BottomSheetDialog.show]; the window's insets exist only once it is attached. */
    fun centreInSafeArea(dialog: BottomSheetDialog) {
        val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        sheet.post { apply(sheet) }
        sheet.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> apply(v) }
    }

    private fun apply(sheet: View) {
        val insets = ViewCompat.getRootWindowInsets(sheet) ?: return
        val s = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val lp = sheet.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (lp.leftMargin == s.left && lp.rightMargin == s.right) return
        lp.leftMargin = s.left
        lp.rightMargin = s.right
        sheet.layoutParams = lp
    }
}
