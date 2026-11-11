package com.zetzzzz.zaplauncher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Zap Launcher settings (v0.1). Everything beyond the home grid lives here:
 * wallpaper, hidden apps, favorite order, default-home shortcut, About.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var list: RecyclerView

    private val pickWallpaper = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) { /* best effort */ }
            prefs.wallpaper = Prefs.WALLPAPER_CUSTOM_PREFIX + uri.toString()
            refreshRows()
        }
    }

    private data class Row(val title: String, val subtitle: String, val action: () -> Unit)

    private fun rows(): List<Row> {
        val wallpaperName = when {
            prefs.wallpaper.startsWith(Prefs.WALLPAPER_CUSTOM_PREFIX) -> "Custom image"
            prefs.wallpaper == Prefs.WALLPAPER_LAVENDER -> "Lavender"
            prefs.wallpaper == Prefs.WALLPAPER_FOREST -> "Forest"
            prefs.wallpaper == Prefs.WALLPAPER_MIDNIGHT -> "Midnight"
            else -> "Default (dusk)"
        }
        return listOf(
            Row("Wallpaper", "Current: $wallpaperName") { showWallpaperDialog() },
            Row("Hidden apps", "Hide apps from the home grid") {
                startActivity(Intent(this, HideAppsActivity::class.java))
            },
            Row("Favorite apps order", "Reorder the pinned apps at the top") {
                startActivity(Intent(this, FavoritesActivity::class.java))
            },
            Row("Make Zap Launcher the default home", "Open the system home setting") {
                try {
                    startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            },
            Row("About Zap Launcher", "Version, license and info") { showAboutDialog() }
        )
    }

    private fun showWallpaperDialog() {
        val names = arrayOf("Default (dusk)", "Lavender", "Forest", "Midnight", "Choose an image…")
        val values = arrayOf(
            Prefs.WALLPAPER_DEFAULT, Prefs.WALLPAPER_LAVENDER,
            Prefs.WALLPAPER_FOREST, Prefs.WALLPAPER_MIDNIGHT, "custom"
        )
        val current = when {
            prefs.wallpaper.startsWith(Prefs.WALLPAPER_CUSTOM_PREFIX) -> -1
            prefs.wallpaper == Prefs.WALLPAPER_LAVENDER -> 1
            prefs.wallpaper == Prefs.WALLPAPER_FOREST -> 2
            prefs.wallpaper == Prefs.WALLPAPER_MIDNIGHT -> 3
            else -> 0
        }
        AlertDialog.Builder(this)
            .setTitle("Wallpaper")
            .setSingleChoiceItems(names, current) { dialog, which ->
                if (values[which] == "custom") {
                    dialog.dismiss()
                    pickWallpaper.launch("image/*")
                } else {
                    prefs.wallpaper = values[which]
                    dialog.dismiss()
                    refreshRows()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Zap Launcher")
            .setMessage(
                "Version 0.1.0\n\n" +
                "A lightweight, fast, ad-free custom launcher for Android TV.\n" +
                "No ads, no tracking, no background services.\n\n" +
                "Source: github.com/zetzzzz/ZapLauncher\n" +
                "License: MIT"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        list = findViewById(R.id.settingsList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = SettingsAdapter(rows())
    }

    override fun onResume() {
        super.onResume()
        refreshRows()
    }

    private fun refreshRows() {
        (list.adapter as? SettingsAdapter)?.submit(rows())
    }

    private class SettingsAdapter(private var items: List<Row>) :
        RecyclerView.Adapter<SettingsAdapter.Holder>() {

        fun submit(list: List<Row>) {
            items = list
            notifyDataSetChanged()
        }

        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.rowTitle)
            val subtitle: TextView = v.findViewById(R.id.rowSubtitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_setting, parent, false)
            return Holder(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val row = items[position]
            h.title.text = row.title
            h.subtitle.text = row.subtitle
            h.itemView.setOnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.02f else 1f)
                    .scaleY(if (hasFocus) 1.02f else 1f).setDuration(120).start()
            }
            h.itemView.setOnClickListener { row.action() }
        }
    }
}
