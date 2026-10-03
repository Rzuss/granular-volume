package com.granularvolume.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Play flavor: "a newer version is ready" (1.7.0).
 *
 * Why it exists. Measured on the 1.5.0 rollout: a third of active devices had the new version
 * the same day, about three quarters after three days, and a few percent were still on a
 * six-week-old version. The app cannot know an update exists (it has no network permission),
 * and its screens are rarely opened, so a fix could sit in the store while the person it was
 * written for kept the bug.
 *
 * How it works without the network. Every call here is IPC to the Play Store app on the
 * device, exactly like billing and the review prompt: the Store knows what it has, and the
 * Store does the download. This process still cannot open a socket; the merged manifest of
 * the built APK carries no INTERNET permission, which the release check proves on the artifact.
 *
 * What the user sees, and all of it: a row at the top of "Your access" and a small dot on the
 * dial's info button. No notification, no pop-up over their content, and nothing happens
 * until they press Update, which opens Google's own update screen.
 *
 * Threading: creating Google's update manager does synchronous package-manager work. The
 * first build of this file did it on the main thread, in the access sheet's onResume, and a
 * loaded emulator answered with "Quiet Dial isn't responding" (2026-10-03, main thread inside
 * AppUpdateManagerFactory.create). Same lesson as billing: every entry point hops to a worker
 * first, and answers come back on the main thread.
 */
object UpdateCheck {

    private const val TAG = "GranularVolume:Update"
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val REQUEST_CODE = 7001

    private val main = Handler(Looper.getMainLooper())
    private val worker: Handler by lazy {
        Handler(HandlerThread("GranularVolume:Update").apply { start() }.looper)
    }

    /** True on this flavor: the access sheet may show the row. */
    const val isSupported = true

    /** What the last check found, without asking again. Never true for the version that is installed. */
    fun isKnownAvailable(context: Context): Boolean =
        Prefs.getUpdateAvailableVersion(context) > installedVersion(context)

    /** At most one question a day; the service calls this on start. */
    fun checkDaily(context: Context, onResult: (Boolean) -> Unit) {
        val now = System.currentTimeMillis()
        val last = Prefs.getUpdateCheckedAt(context)
        // A clock that moved backwards must not silence the check for good.
        if (last in 1..now && now - last < DAY_MS) {
            onResult(isKnownAvailable(context))
            return
        }
        checkNow(context, onResult)
    }

    /**
     * Asks the Play Store app now. Answers on the main thread. A check that cannot be answered
     * (no Store, not installed from Play, the Store busy) keeps the previous answer: an outage
     * must neither invent an update nor hide one.
     */
    fun checkNow(context: Context, onResult: (Boolean) -> Unit) {
        val ctx = context.applicationContext
        worker.post { checkOnWorker(ctx) { available -> main.post { onResult(available) } } }
    }

    private fun checkOnWorker(ctx: Context, onResult: (Boolean) -> Unit) {
        try {
            AppUpdateManagerFactory.create(ctx).appUpdateInfo
                .addOnSuccessListener { info ->
                    val offered = when (info.updateAvailability()) {
                        UpdateAvailability.UPDATE_AVAILABLE,
                        UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> info.availableVersionCode()
                        else -> 0
                    }
                    Prefs.setUpdateAvailableVersion(ctx, offered)
                    Prefs.setUpdateCheckedAt(ctx, System.currentTimeMillis())
                    Log.i(TAG, "Update check: availability=${info.updateAvailability()} offered=$offered installed=${installedVersion(ctx)}")
                    onResult(isKnownAvailable(ctx))
                }
                .addOnFailureListener { e ->
                    Log.i(TAG, "Update check: no answer (${e.message}), previous answer kept")
                    onResult(isKnownAvailable(ctx))
                }
        } catch (t: Throwable) {
            // A broken Play Store must read as "no answer", never as a crash in our process.
            Log.w(TAG, "Update check threw: ${t.message}")
            onResult(isKnownAvailable(ctx))
        }
    }

    /**
     * Opens Google's own update screen over [activity]: it downloads, installs and restarts
     * the app. When the Store will not run that flow here, the app's store page opens instead,
     * where the same Update button lives. The running control comes back by itself after the
     * update (see BootReceiver, MY_PACKAGE_REPLACED).
     */
    fun startUpdate(activity: Activity) {
        worker.post { startOnWorker(activity) }
    }

    private fun startOnWorker(activity: Activity) {
        try {
            val manager = AppUpdateManagerFactory.create(activity.applicationContext)
            // The listeners below run on the main thread (the Task default), where the flow must start.
            manager.appUpdateInfo
                .addOnSuccessListener { info ->
                    val resumable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                        info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                    if (activity.isFinishing || activity.isDestroyed) return@addOnSuccessListener
                    if (resumable && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                        runCatching {
                            manager.startUpdateFlowForResult(
                                info, activity,
                                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                                REQUEST_CODE
                            )
                        }.onSuccess { Log.i(TAG, "Update flow opened") }
                            .onFailure { Log.w(TAG, "Update flow failed: ${it.message}"); openStorePage(activity) }
                    } else {
                        Log.i(TAG, "Update flow not offered here, opening the store page")
                        openStorePage(activity)
                    }
                }
                .addOnFailureListener { openStorePage(activity) }
        } catch (t: Throwable) {
            Log.w(TAG, "startUpdate threw: ${t.message}")
            main.post { openStorePage(activity) }
        }
    }

    private fun openStorePage(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val pkg = activity.packageName
        runCatching {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).setPackage("com.android.vending")
            )
        }.onFailure {
            runCatching {
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                )
            }
        }
    }

    private fun installedVersion(context: Context): Int = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
    } catch (_: Exception) {
        Int.MAX_VALUE   // unknown: never claim an update over a version we cannot read
    }
}
