package com.granularvolume.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * The web pages the app can hand to the browser. The app itself never loads them: it has no
 * network permission, and each of these is an ACTION_VIEW that another app answers.
 */
object Links {
    /** 1.7.0: setup, a control that stops by itself, restoring a purchase, device limits. */
    const val HELP = "https://granularvolume.com/help.html"

    /** 1.7.2: the help section for someone who cannot find the app in Android's permission list. */
    const val HELP_FIND = "https://granularvolume.com/help.html#find"

    /** 1.7.6: the developer's address, for the "write to us" link on the locked sheets. */
    const val SUPPORT_EMAIL = "rotemzus@gmail.com"

    /**
     * 1.7.6: opens the person's own mail app on a new message to the developer, so someone
     * stopped by the lock can ask instead of guessing. The app sends nothing itself: this is an
     * ACTION_SENDTO that the mail app answers, and only the subject (with the version, to help
     * answer) is filled in. Without a mail app, the address is shown so it can be copied.
     *
     * 1.7.8: the body starts with the device, the Android version and how the quiet steps are
     * attached, the three facts every answer begins with. It is plain text in the person's own
     * draft, visible and editable before they choose to send it.
     */
    fun writeToUs(context: Context) {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
        val subject = context.getString(com.granularvolume.R.string.gv_write_subject, version)
        val path = when (ControlLive.effect) {
            ControlLive.Effect.PREFERRED -> "standard"
            ControlLive.Effect.FALLBACK -> "fallback"
            ControlLive.Effect.NONE -> "none"
            ControlLive.Effect.UNKNOWN -> "not running"
        }
        val body = context.getString(
            com.granularvolume.R.string.gv_write_body,
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            android.os.Build.VERSION.RELEASE,
            path
        )
        val uri = Uri.parse(
            "mailto:$SUPPORT_EMAIL?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body)
        )
        val sent = runCatching {
            context.startActivity(Intent(Intent.ACTION_SENDTO, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!sent) {
            android.widget.Toast.makeText(
                context,
                context.getString(com.granularvolume.R.string.gv_write_no_app, SUPPORT_EMAIL),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    fun open(context: Context, url: String) {
        // Android's own log shortens the address of a VIEW request on older versions, so the
        // app says which page it asked for. These are fixed public addresses, nothing personal.
        android.util.Log.i("GranularVolume", "open link: $url")
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
