package com.zetzzzz.zaplauncher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.util.LruCache
import androidx.leanback.widget.VerticalGridView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Zap Launcher home screen (v0.1).
 *
 * Full-screen wallpaper, a centered app grid (Wolf-style), live clock + date
 * and a settings gear at the top right. Everything else lives in Settings.
 * No ads, no tracking, no background services.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var wallpaperView: ImageView
    private lateinit var appGrid: VerticalGridView
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var settingsButton: View
    private lateinit var adapter: AppAdapter

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        wallpaperView = findViewById(R.id.wallpaper)
        appGrid = findViewById(R.id.appGrid)
        clockText = findViewById(R.id.clockText)
        dateText = findViewById(R.id.dateText)
        settingsButton = findViewById(R.id.settingsButton)

        appGrid.setNumColumns(4)
        adapter = AppAdapter(packageManager)
        appGrid.adapter = adapter
        // NOTE (v0.1.1): no OnChildSelectedListener here. Calling
        // bringToFront() from the selection callback crashed on first focus
        // ("Cannot call this method while RecyclerView is computing a
        // layout"). The focus scale animation in onBindViewHolder already
        // makes the focused tile stand out.

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        settingsButton.setOnFocusChangeListener { v, hasFocus ->
            v.animate().scaleX(if (hasFocus) 1.2f else 1f)
                .scaleY(if (hasFocus) 1.2f else 1f).setDuration(120).start()
        }

        updateClock()
        applyWallpaper()
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        clockHandler.post(clockTick)
        applyWallpaper()
        loadApps()
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockTick)
    }

    /** Home screen: Back does nothing. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* stay on home */ }

    private fun updateClock() {
        val now = Date()
        clockText.text = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(now)
        dateText.text = SimpleDateFormat("EEE d MMM yyyy", Locale.ENGLISH).format(now)
    }

    private fun applyWallpaper() {
        val w = prefs.wallpaper
        if (w.startsWith(Prefs.WALLPAPER_CUSTOM_PREFIX)) {
            val uri = Uri.parse(w.removePrefix(Prefs.WALLPAPER_CUSTOM_PREFIX))
            thread {
                val bmp = decodeSampledBitmap(uri, 1920, 1080)
                runOnUiThread {
                    if (bmp != null) {
                        wallpaperView.setImageDrawable(BitmapDrawable(resources, bmp))
                    } else {
                        wallpaperView.setImageResource(R.drawable.bg_default)
                    }
                }
            }
        } else {
            val res = when (w) {
                Prefs.WALLPAPER_LAVENDER -> R.drawable.bg_lavender
                Prefs.WALLPAPER_FOREST -> R.drawable.bg_forest
                Prefs.WALLPAPER_MIDNIGHT -> R.drawable.bg_midnight
                else -> R.drawable.bg_default
            }
            wallpaperView.setImageResource(res)
        }
    }

    private fun decodeSampledBitmap(uri: Uri, reqW: Int, reqH: Int) =
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / sample > reqW || bounds.outHeight / sample > reqH) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (e: Exception) {
            null
        }

    private fun loadApps() {
        thread {
            val all = AppsRepository.loadApps(packageManager, packageName)
            val hidden = prefs.hiddenApps()
            var favs = prefs.favorites()
            if (favs.isEmpty()) {
                favs = AppsRepository.defaultFavorites(all)
                prefs.setFavorites(favs)
            }
            val visible = all.filter { it.packageName !in hidden }
            // Favorites first (in saved order), then the rest alphabetically.
            val ordered = visible.sortedWith(
                compareBy(
                    { entry -> favs.indexOf(entry.packageName).let { if (it < 0) Int.MAX_VALUE else it } },
                    { it.label.lowercase() }
                )
            )
            runOnUiThread {
                adapter.submit(ordered)
                // Give the first tile focus so the remote works immediately.
                appGrid.post {
                    appGrid.getChildAt(0)?.requestFocus()
                        ?: appGrid.requestFocus()
                }
            }
        }
    }

    private inner class AppAdapter(private val pm: PackageManager) :
        androidx.recyclerview.widget.RecyclerView.Adapter<AppAdapter.Holder>() {

        private var items: List<AppEntry> = emptyList()
        private val iconCache = LruCache<String, Drawable>(48)

        fun submit(list: List<AppEntry>) {
            items = list
            notifyDataSetChanged()
        }

        inner class Holder(v: View) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.appIcon)
            val label: TextView = v.findViewById(R.id.appLabel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app, parent, false)
            return Holder(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val app = items[position]
            h.label.text = app.label
            val cached = iconCache.get(app.packageName)
            if (cached != null) {
                h.icon.setImageDrawable(cached)
            } else {
                thread {
                    val drawable = try {
                        pm.getApplicationIcon(app.packageName)
                    } catch (e: PackageManager.NameNotFoundException) {
                        getDrawable(R.drawable.ic_zap)
                    }
                    if (drawable != null) iconCache.put(app.packageName, drawable)
                    runOnUiThread {
                        @Suppress("DEPRECATION")
                        if (h.adapterPosition == position) {
                            h.icon.setImageDrawable(drawable)
                        }
                    }
                }
            }
            h.itemView.setOnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.14f else 1f)
                    .scaleY(if (hasFocus) 1.14f else 1f).setDuration(120).start()
            }
            h.itemView.setOnClickListener {
                val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                    ?: Intent(Intent.ACTION_MAIN).apply {
                        component = ComponentName(app.packageName, app.activityName)
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    // App vanished between listing and launch; ignore.
                }
            }
            // D-pad centre key also activates the focused tile.
            h.itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    h.itemView.performClick()
                    true
                } else false
            }
        }
    }
}
