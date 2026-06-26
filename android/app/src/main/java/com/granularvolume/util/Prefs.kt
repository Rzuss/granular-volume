package com.granularvolume.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Type-safe SharedPreferences wrapper.
 * All keys are constants — no magic strings outside this class.
 */
object Prefs {

    private const val FILE_NAME = "gv_prefs"

    private const val KEY_ATTENUATION_DB   = "attenuation_db"
    private const val KEY_OVERLAY_X        = "overlay_x"
    private const val KEY_OVERLAY_Y        = "overlay_y"
    private const val KEY_SERVICE_WAS_RUNNING = "service_was_running"
    private const val KEY_COLLAPSED        = "overlay_collapsed"

    /** Current attenuation in dB (0.0 = none, -30.0 = near-silent) */
    const val ATTENUATION_DEFAULT = 0f
    const val ATTENUATION_MIN     = -30f
    const val ATTENUATION_MAX     = 0f

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun getAttenuation(context: Context): Float =
        prefs(context).getFloat(KEY_ATTENUATION_DB, ATTENUATION_DEFAULT)

    fun setAttenuation(context: Context, dB: Float) {
        prefs(context).edit { putFloat(KEY_ATTENUATION_DB, dB.coerceIn(ATTENUATION_MIN, ATTENUATION_MAX)) }
    }

    fun getOverlayX(context: Context, default: Int): Int =
        prefs(context).getInt(KEY_OVERLAY_X, default)

    fun getOverlayY(context: Context, default: Int): Int =
        prefs(context).getInt(KEY_OVERLAY_Y, default)

    fun setOverlayPosition(context: Context, x: Int, y: Int) {
        prefs(context).edit {
            putInt(KEY_OVERLAY_X, x)
            putInt(KEY_OVERLAY_Y, y)
        }
    }

    fun setServiceWasRunning(context: Context, running: Boolean) {
        prefs(context).edit { putBoolean(KEY_SERVICE_WAS_RUNNING, running) }
    }

    fun wasServiceRunning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SERVICE_WAS_RUNNING, false)

    fun isCollapsed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COLLAPSED, false)

    fun setCollapsed(context: Context, collapsed: Boolean) {
        prefs(context).edit { putBoolean(KEY_COLLAPSED, collapsed) }
    }
}
