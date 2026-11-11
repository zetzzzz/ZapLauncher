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

    companion object {
        const val KEY_WALLPAPER = "wallpaper"
        const val KEY_HIDDEN = "hidden_apps"
        const val KEY_FAVORITES = "favorites"
        const val WALLPAPER_DEFAULT = "default"
        const val WALLPAPER_LAVENDER = "lavender"
        const val WALLPAPER_FOREST = "forest"
        const val WALLPAPER_MIDNIGHT = "midnight"
        const val WALLPAPER_CUSTOM_PREFIX = "custom:"
    }
}
