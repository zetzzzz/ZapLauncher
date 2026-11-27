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

    /** Packages pinned at the top of the grid when installed, in this order.
     * "tizentube" is the user's TizenTube (they call it SmartTube). */
    private val PINNED_ORDER = listOf("nuvio", "smarttube", "tizentube", "moonlight")

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

    /**
     * Full home-grid order for the given apps, persisted in [Prefs].
     * First run migrates the old favorites list (plus any pinned app the
     * old list missed); afterwards the saved order is kept, pruned to
     * installed apps. Newly installed pinned apps (Nuvio/SmartTube/
     * Moonlight) are inserted right after the existing pinned block
     * instead of at the end, so an app installed later still lands at
     * the top.
     */
    fun resolveOrder(apps: List<AppEntry>, prefs: Prefs): List<String> {
        val installed = apps.map { it.packageName }.toSet()
        val byPkg = apps.associateBy { it.packageName }
        val isPinned = { pkg: String ->
            byPkg[pkg]?.let { pinIndex(it) != Int.MAX_VALUE } == true
        }
        val saved = prefs.appOrder().filter { it in installed }.toMutableList()
        // One-time repair (v0.1.4): pinned apps stranded in the
        // alphabetical tail by the old favorites seeding move up behind
        // the pinned block, in pin order. After this, the user's own
        // arrangement is never touched.
        if (!prefs.orderRepaired) {
            var prefixEnd = 0
            while (prefixEnd < saved.size && isPinned(saved[prefixEnd])) prefixEnd++
            val stranded = saved.subList(prefixEnd, saved.size)
                .filter { isPinned(it) }
                .sortedBy { pinIndex(byPkg[it]!!) }
            if (stranded.isNotEmpty()) {
                saved.removeAll(stranded.toSet())
                var at = 0
                while (at < saved.size && isPinned(saved[at])) at++
                saved.addAll(at, stranded)
            }
            prefs.orderRepaired = true
        }
        if (saved.isEmpty()) {
            // Migrate: old favorites first, then any pinned app the old
            // list missed, then the rest A-Z.
            val favs = prefs.favorites().filter { it in installed }
            val pinnedMissing = defaultFavorites(apps).filter { it !in favs }
            val seed = favs + pinnedMissing
            val rest = apps.filter { it.packageName !in seed }
                .sortedBy { it.label.lowercase() }
                .map { it.packageName }
            saved += seed + rest
        } else {
            // Merge newly installed apps.
            val known = saved.toSet()
            val fresh = apps.filter { it.packageName !in known }
            val freshPinned = fresh.filter { pinIndex(it) != Int.MAX_VALUE }
                .sortedBy { pinIndex(it) }
            val freshRest = fresh.filter { pinIndex(it) == Int.MAX_VALUE }
                .sortedBy { it.label.lowercase() }
            if (freshPinned.isNotEmpty()) {
                // Insert after the last pinned app already in the order.
                var insertAt = 0
                saved.forEachIndexed { i, pkg ->
                    if (isPinned(pkg)) insertAt = i + 1
                }
                saved.addAll(insertAt, freshPinned.map { it.packageName })
            }
            saved += freshRest.map { it.packageName }
        }
        prefs.setAppOrder(saved)
        return saved
    }
}
