package de.rosberg.onepluscamerabridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.provider.MediaStore

data class CameraHandler(
    val component: ComponentName,
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val score: Int
) {
    fun display(): String = "$label\n$packageName/${component.className.substringAfterLast('.')}" +
        if (isSystem) "  [системная]" else ""
}

object CameraDiscovery {
    fun find(context: Context, action: String = MediaStore.ACTION_IMAGE_CAPTURE): List<CameraHandler> {
        val intent = Intent(action).addCategory(Intent.CATEGORY_DEFAULT)
        val results: List<ResolveInfo> = context.packageManager.queryIntentActivities(
            intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )
        return results.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            if (activity.packageName == context.packageName) return@mapNotNull null
            val app = activity.applicationInfo
            val system = (app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            val packageLower = activity.packageName.lowercase()
            val classLower = activity.name.lowercase()
            val label = info.loadLabel(context.packageManager)?.toString().orEmpty().ifBlank { activity.packageName }
            var score = if (system) 100 else 0
            if ("camera" in packageLower) score += 30
            if ("camera" in classLower) score += 15
            if ("oneplus" in packageLower || "oplus" in packageLower || "coloros" in packageLower) score += 40
            CameraHandler(ComponentName(activity.packageName, activity.name), label, activity.packageName, system, score)
        }.distinctBy { it.component.flattenToString() }
            .sortedWith(compareByDescending<CameraHandler> { it.score }.thenBy { it.label.lowercase() })
    }

    fun choose(context: Context, handlers: List<CameraHandler>): CameraHandler? {
        val preferred = Prefs.preferredCamera(context)
        return handlers.firstOrNull { it.component.flattenToString() == preferred }
            ?: handlers.firstOrNull { it.isSystem && it.score >= 130 }
            ?: handlers.firstOrNull { it.isSystem }
    }
}
