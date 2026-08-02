package de.rosberg.onepluscamerabridge

import android.content.Context

object Prefs {
    private const val FILE = "bridge_settings"
    private const val CAMERA = "preferred_camera"
    private const val PENDING = "pending_temp_path"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun preferredCamera(context: Context): String? = prefs(context).getString(CAMERA, null)
    fun setPreferredCamera(context: Context, component: String?) {
        prefs(context).edit().apply {
            if (component == null) remove(CAMERA) else putString(CAMERA, component)
        }.apply()
    }

    fun pendingPath(context: Context): String? = prefs(context).getString(PENDING, null)
    fun setPendingPath(context: Context, path: String?) {
        prefs(context).edit().apply {
            if (path == null) remove(PENDING) else putString(PENDING, path)
        }.commit()
    }
}
