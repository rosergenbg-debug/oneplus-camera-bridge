package de.rosberg.onepluscamerabridge

import android.content.Context
import java.io.File

object TempFiles {
    private const val DIR = "camera_bridge"
    private const val STALE_MS = 60 * 60 * 1000L

    fun create(context: Context): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        return File.createTempFile("capture_", ".jpg", dir)
    }

    fun delete(file: File?): Boolean = file?.let { !it.exists() || it.delete() } ?: true

    fun cleanupAll(context: Context): Int {
        val files = File(context.cacheDir, DIR).listFiles().orEmpty()
        var deleted = 0
        files.forEach { if (it.isFile && it.delete()) deleted++ }
        File(context.cacheDir, DIR).delete()
        return deleted
    }

    fun cleanupStale(context: Context, keepPath: String?): Int {
        val now = System.currentTimeMillis()
        var deleted = 0
        File(context.cacheDir, DIR).listFiles().orEmpty().forEach { file ->
            val age = now - file.lastModified()
            val isRecentPending = file.absolutePath == keepPath && age <= STALE_MS
            if (!isRecentPending && age > STALE_MS && file.delete()) deleted++
        }
        return deleted
    }
}
