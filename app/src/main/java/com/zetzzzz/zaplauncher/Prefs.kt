package com.zetzzzz.zaplauncher

import android.content.Context
import android.content.SharedPreferences

/** Tiny SharedPreferences wrapper. No database, no files, no cloud. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("zap_launcher", Context.MODE_PRIVATE)

    var wallpaper: String
        get() = sp.getString(KEY_WALLPAPER, WALLPAPER_DEFAULT) ?: WALLPAPER_DEFAULT
        set(v) = sp.edit().putString(KEY_WALLPAPER, v).apply()

    fun hiddenApps(): Set<String> = sp.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
    fun setHiddenApps(pkgs: Set<String>) = sp.edit().putStringSet(KEY_HIDDEN, pkgs).apply()

    /** Ordered favorite package names, stored as a single |-separated string. */
    fun favorites(): List<String> {
        val raw = sp.getString(KEY_FAVORITES, "") ?: ""
        return raw.split("|").filter { it.isNotBlank() }
    }
    fun setFavorites(pkgs: List<String>) =
        sp.edit().putString(KEY_FAVORITES, pkgs.joinToString("|")).apply()

    /** Full home-grid order: every visible app, in display order. */
    fun appOrder(): List<String> {
        val raw = sp.getString(KEY_APP_ORDER, "") ?: ""
        return raw.split("|").filter { it.isNotBlank() }
    }
    fun setAppOrder(pkgs: List<String>) =
        sp.edit().putString(KEY_APP_ORDER, pkgs.joinToString("|")).apply()

    /** One-time v0.1.4 repair of stranded pinned apps has run. */
    var orderRepaired: Boolean
        get() = sp.getBoolean(KEY_ORDER_REPAIRED, false)
        set(v) = sp.edit().putBoolean(KEY_ORDER_REPAIRED, v).apply()

    /** Custom icons: package name -> image content URI ("pkg|uri" entries). */
    fun customIcon(pkg: String): String? {
        val raw = sp.getStringSet(KEY_CUSTOM_ICONS, emptySet()) ?: emptySet()
        val prefix = "$pkg|"
        return raw.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
    }
    fun setCustomIcon(pkg: String, uri: String?) {
        val current = (sp.getStringSet(KEY_CUSTOM_ICONS, emptySet()) ?: emptySet()).toMutableSet()
        val prefix = "$pkg|"
        current.removeAll { it.startsWith(prefix) }
        if (uri != null) current.add(prefix + uri)
        sp.edit().putStringSet(KEY_CUSTOM_ICONS, current).apply()
    }

    companion object {
        const val KEY_WALLPAPER = "wallpaper"
        const val KEY_HIDDEN = "hidden_apps"
        const val KEY_FAVORITES = "favorites"
        const val KEY_APP_ORDER = "app_order"
        const val KEY_ORDER_REPAIRED = "order_repaired_v014"
        const val KEY_CUSTOM_ICONS = "custom_icons"
        const val WALLPAPER_DEFAULT = "default"
        const val WALLPAPER_LAVENDER = "lavender"
        const val WALLPAPER_FOREST = "forest"
        const val WALLPAPER_MIDNIGHT = "midnight"
        const val WALLPAPER_CUSTOM_PREFIX = "custom:"
    }
}
