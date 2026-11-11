package com.zetzzzz.zaplauncher

import android.content.Intent
import android.content.pm.PackageManager

data class AppEntry(
    val packageName: String,
    val label: String,
    val activityName: String
)

/**
 * Loads every launchable app on the TV. Both LEANBACK_LAUNCHER and plain
 * LAUNCHER categories are queried so sideloaded apps are never hidden
 * (the same behaviour Wolf Launcher is known for).
 */
object AppsRepository {

    /** Packages pinned at the top of the grid when installed, in this order. */
    private val PINNED_ORDER = listOf("nuvio", "smarttube", "moonlight")

    private fun pinIndex(entry: AppEntry): Int {
        val label = entry.label.lowercase()
        val pkg = entry.packageName.lowercase()
        for ((i, key) in PINNED_ORDER.withIndex()) {
            if (label.contains(key) || pkg.contains(key)) return i
        }
        return Int.MAX_VALUE
    }

    fun loadApps(pm: PackageManager, selfPackage: String): List<AppEntry> {
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        )
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>()
        for (intent in intents) {
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == selfPackage) continue
                if (!seen.add(ai.packageName)) continue
                out += AppEntry(ai.packageName, ri.loadLabel(pm).toString(), ai.name)
            }
        }
        // Favorites (Nuvio TV, SmartTube, Moonlight) first, then A-Z by label.
        out.sortWith(compareBy({ pinIndex(it) }, { it.label.lowercase() }))
        return out
    }

    /** Default favorites: the pinned apps that are actually installed. */
    fun defaultFavorites(apps: List<AppEntry>): List<String> =
        apps.filter { pinIndex(it) != Int.MAX_VALUE }
            .sortedBy { pinIndex(it) }
            .map { it.packageName }
}
