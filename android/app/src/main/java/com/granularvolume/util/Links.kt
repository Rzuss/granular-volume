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

    fun open(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
