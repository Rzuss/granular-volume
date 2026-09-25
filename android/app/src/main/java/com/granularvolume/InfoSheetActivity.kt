package com.granularvolume

import android.content.Intent
import android.content.Context
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.granularvolume.service.VolumeControlService
import com.granularvolume.audio.StreamVolumeController
import com.granularvolume.util.Entitlement
import com.granularvolume.util.KeyCheck
import com.granularvolume.util.ProAccess
import com.granularvolume.util.Prefs

/**
 * "Your access": the sheet behind the dial's info button.
 *
 * Why a sheet and not a screen. The dial exists for late-night listening next to someone
 * asleep. A full, bright screen taking over the display at 2am is the opposite of what
 * this app promises, so this slides up over whatever is running and closes on a tap
 * outside. Same component and palette as the paywall sheet, so the two moments a user
 * meets our commercial side look like one product rather than two.
 *
 * It is also the ONLY place several things can be reached at all:
 *  - during the first four days of a trial nothing is locked, so the paywall never opens
 *    and there was otherwise no way for a convinced user to pay us
 *  - a long-time user who dismissed the one-time card had no route back to supporting us
 *  - the legal texts were reachable only from a screen that closes itself once set up
 *
 * It reports access and offers Play without writing entitlement. The Bluetooth floor
 * control stores only the listener's calibration for the connected media output.
 */
class InfoSheetActivity : AppCompatActivity() {

    private var dialog: BottomSheetDialog? = null

    /** The state this sheet last rendered, so a resume can tell a purchase from a mere return. */
    private var lastState: State? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialog = BottomSheetDialog(this).apply {
            setContentView(buildSheet())
            setOnCancelListener { finish() }
            show()
        }
    }

    /**
     * Returning from Play with the key installed: re-render rather than show stale state, AND
     * tell the service. This sheet is the only purchase route on trial days 7 to 4 and the
     * route every locked notification opens, and until 2026-09-09 a purchase made from here
     * told the service nothing: the audio latch would open on the next dial touch anyway, but
     * the shade kept saying "Locked" and the buyer got no acknowledgement at all. Same signal
     * PaywallActivity sends, so the two purchase routes now end identically.
     */
    override fun onResume() {
        super.onResume()
        val before = lastState
        dialog?.setContentView(buildSheet())
        val after = lastState
        if (after == State.UNLOCKED && before != null && before != State.UNLOCKED) {
            startService(
                Intent(this, VolumeControlService::class.java)
                    .setAction(VolumeControlService.ACTION_KEY_INSTALLED)
            )
            // No toast: the service announces the purchase itself (see PaywallActivity.onResume).
        }
    }

    override fun onDestroy() {
        dialog?.setOnCancelListener(null)
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }

    // -- state ----------------------------------------------------------------

    private enum class State { FDROID, GRANDFATHERED, TRIAL, UNLOCKED, LOCKED }

    /**
     * Order matters and mirrors [ProAccess.isPro]: a grandfathered device that also owns
     * the key is reported as grandfathered, because that is the promise we made and the
     * one they would be upset to see disappear.
     */
    private fun state(): State = when {
        // The F-Droid check comes first and is a FLAVOR check, never a KeyCheck one: the
        // F-Droid KeyCheck stub answers true by design, and reading it here would tell
        // every F-Droid user "Unlocked with the Full Range Key. Thank you." -- gratitude
        // for a purchase that never happened, on the one surface whose audience checks.
        BuildConfig.FLAVOR != "play" -> State.FDROID
        Entitlement.isGrandfathered(this) -> State.GRANDFATHERED
        KeyCheck.isKeyInstalled(this) -> State.UNLOCKED
        Entitlement.isTrialActive(this) -> State.TRIAL
        else -> State.LOCKED
    }

    // -- sheet ----------------------------------------------------------------

    /**
     * 1.4.4: the sheet's content is built as a plain LinearLayout, so at a large system font
     * scale it grew past the sheet and the actions at the bottom were simply unreachable.
     * A NestedScrollView keeps the bottom-sheet drag working while letting the content scroll.
     */
    private fun View.inScroller(): View = NestedScrollView(this@InfoSheetActivity).apply {
        isFillViewport = true
        addView(
            this@inScroller,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun buildSheet(): View {
        val st = state()
        lastState = st
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.gv_surface))
            val p = dp(24)
            setPadding(p, p, p, dp(28))
        }

        val headline = when (st) {
            State.FDROID -> getString(R.string.gv_info_state_fdroid)
            State.GRANDFATHERED -> getString(R.string.gv_info_state_grandfathered)
            State.UNLOCKED -> getString(R.string.gv_info_state_unlocked)
            State.LOCKED -> getString(R.string.gv_info_state_locked)
            State.TRIAL -> {
                val d = Entitlement.daysLeftInTrial(this)
                resources.getQuantityString(R.plurals.gv_trial_days_left, d, d)
            }
        }
        root.addView(text(headline, 19f, bold = true, colorRes = R.color.gv_text_primary))

        val body = when (st) {
            State.FDROID -> R.string.gv_info_body_fdroid
            State.GRANDFATHERED -> R.string.gv_info_body_grandfathered
            State.UNLOCKED -> R.string.gv_info_body_unlocked
            // On the final day the generic trial body would bury the one fact that matters.
            State.TRIAL ->
                if (Entitlement.daysLeftInTrial(this) <= 1) R.string.gv_info_body_trial_last_day
                else R.string.gv_info_body_trial
            State.LOCKED -> R.string.gv_info_body_locked
        }
        root.addView(text(getString(body), 13f, colorRes = R.color.gv_text_secondary).topPad(8))
        bluetoothFloorControls()?.let { root.addView(it.topPad(18, fill = true)) }

        // A buyer is offered nothing: they already paid, and a live "buy" button would read
        // as a second charge. F-Droid is offered nothing either: that build has no key and
        // pointing its users at Google Play would betray the promise the listing makes.
        // Everyone else gets one honest route to Play, worded as support for the people
        // who owe us nothing.
        if (st != State.UNLOCKED && st != State.FDROID) {
            val cta = if (st == State.GRANDFATHERED) R.string.gv_info_cta_support
            else R.string.gv_info_cta_buy
            root.addView(Button(this).apply {
                text = getString(cta)
                isAllCaps = false
                setTextColor(ContextCompat.getColor(context, R.color.gv_on_accent))
                setBackgroundColor(ContextCompat.getColor(context, R.color.gv_accent))
                setOnClickListener { openStore() }
            }.topPad(18, fill = true))
        }

        // The restore note must describe the reader's OWN restore. A grandfathered user
        // never bought a key, so "install the key again, it is never charged twice" would
        // be false for them -- their access travels with Android backup instead. F-Droid
        // has neither key nor backup story worth a line here, so it gets none.
        when (st) {
            State.FDROID -> Unit
            State.GRANDFATHERED -> root.addView(
                text(getString(R.string.gv_info_restore_grandfathered), 12f, colorRes = R.color.gv_text_muted)
                    .topPad(14)
            )
            else -> root.addView(
                text(getString(R.string.gv_info_restore), 12f, colorRes = R.color.gv_text_muted)
                    .topPad(if (st == State.UNLOCKED) 18 else 14)
            )
        }

        root.addView(legalRow().topPad(18, fill = true))
        return root.inScroller()
    }

    private fun bluetoothFloorControls(): View? {
        val volume = StreamVolumeController(this)
        val name = volume.activeBluetoothName() ?: return null
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val min = maxOf(1, am.getStreamMinVolume(AudioManager.STREAM_MUSIC))
        val current = Prefs.getBluetoothFloor(this, name).coerceIn(min, max)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("$name: first audible volume step", 14f, bold = true,
                colorRes = R.color.gv_text_primary))
            addView(text("Play audio, then use − or + to find the lowest step you can hear. " +
                "Each tap previews that step without extra attenuation.", 12f,
                colorRes = R.color.gv_text_secondary).topPad(4))
            val row = LinearLayout(this@InfoSheetActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            val value = text("$current", 16f, bold = true, colorRes = R.color.gv_text_primary)
            fun change(delta: Int) {
                val next = (Prefs.getBluetoothFloor(this@InfoSheetActivity, name) + delta)
                    .coerceIn(min, max)
                Prefs.setBluetoothFloor(this@InfoSheetActivity, name, next)
                value.text = "$next"
                startService(Intent(this@InfoSheetActivity, VolumeControlService::class.java)
                    .setAction(VolumeControlService.ACTION_PREVIEW_BLUETOOTH_FLOOR))
            }
            row.addView(Button(this@InfoSheetActivity).apply {
                text = "−"
                contentDescription = "Lower first audible step"
                setOnClickListener { change(-1) }
            })
            row.addView(value.apply { setPadding(dp(18), 0, dp(18), 0) })
            row.addView(Button(this@InfoSheetActivity).apply {
                text = "+"
                contentDescription = "Raise first audible step"
                setOnClickListener { change(1) }
            })
            addView(row.topPad(8))
        }
    }

    /** Terms, Privacy and the licences screen, side by side and always reachable. */
    private fun legalRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        // 1.5.1: replay the feature tour. The service opens the dial if it is docked, then
        // runs the four callouts; the sheet closes so the dial is unobstructed.
        row.addView(link(getString(R.string.gv_tour_replay)) {
            startService(
                Intent(this, VolumeControlService::class.java)
                    .apply { action = VolumeControlService.ACTION_SHOW_TOUR }
            )
            finish()
        })
        row.addView(link(getString(R.string.gv_info_terms)) { openUrl(URL_TERMS) })
        row.addView(link(getString(R.string.gv_info_privacy)) { openUrl(URL_PRIVACY) })
        row.addView(link(getString(R.string.gv_licenses_title)) {
            startActivity(
                Intent(this, LicensesActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        })
        return row
    }

    private fun link(label: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(ContextCompat.getColor(context, R.color.gv_accent_text))
        setPadding(dp(10), dp(8), dp(10), dp(8))
        setOnClickListener { action() }
    }

    private fun openStore() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$KEY_APP_ID")))
        } catch (_: Exception) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$KEY_APP_ID")
                )
            )
        }
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun text(value: String, sizeSp: Float, bold: Boolean = false, colorRes: Int): TextView =
        TextView(this).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(ContextCompat.getColor(context, colorRes))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun <T : View> T.topPad(topDp: Int, fill: Boolean = false): T {
        layoutParams = LinearLayout.LayoutParams(
            if (fill) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(topDp) }
        return this
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val KEY_APP_ID = "com.granularvolume.key"
        const val URL_TERMS = "https://rzuss.github.io/granular-volume-privacy/terms-of-use.html"
        const val URL_PRIVACY = "https://rzuss.github.io/granular-volume-privacy/"
    }
}
