package de.rosberg.onepluscamerabridge

import android.content.Context
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogStore {
    private const val PREFS = "diagnostic_log"
    private const val KEY = "events"
    private const val MAX = 100
    private val lock = Any()

    fun add(context: Context, message: String) = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrElse { JSONArray() }
        val items = ArrayList<String>(MAX)
        val start = maxOf(0, old.length() - (MAX - 1))
        for (i in start until old.length()) items += old.optString(i)
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        items += "$time | ${message.replace('\n', ' ')}"
        prefs.edit().putString(KEY, JSONArray(items).toString()).apply()
    }

    fun text(context: Context): String = synchronized(lock) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        buildString {
            for (i in 0 until array.length()) appendLine(array.optString(i))
        }.trimEnd()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
