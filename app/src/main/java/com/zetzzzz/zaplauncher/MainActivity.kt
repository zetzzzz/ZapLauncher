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
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.util.LruCache
import androidx.leanback.widget.VerticalGridView
import java.io.File
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
        // NOTE (v0.1.4): the gear navigation lives in dispatchKeyEvent
        // below, NOT in a key listener on the grid: Leanback's
        // VerticalGridView consumes DPAD keys for its own focus movement
        // before a grid key listener ever runs, which is why the v0.1.3
        // approach never fired.

        // Date & clock open the system date & time settings (v0.1.3).
        val openDateSettings = {
            try {
                startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (e2: Exception) { /* no settings app; ignore */ }
            }
        }
        val dateFocusScale = View.OnFocusChangeListener { v, hasFocus ->
            v.animate().scaleX(if (hasFocus) 1.1f else 1f)
                .scaleY(if (hasFocus) 1.1f else 1f).setDuration(120).start()
        }
        val dateKey = View.OnKeyListener { v, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                v.performClick()
                true
            } else false
        }
        dateText.onFocusChangeListener = dateFocusScale
        clockText.onFocusChangeListener = dateFocusScale
        dateText.setOnClickListener { openDateSettings() }
        clockText.setOnClickListener { openDateSettings() }
        dateText.setOnKeyListener(dateKey)
        clockText.setOnKeyListener(dateKey)

        updateClock()
        applyWallpaper()
        // NOTE (v0.1.5): no loadApps() here — onResume always runs right
        // after onCreate, and calling it twice re-submitted the grid.
    }

    /**
     * Gear / date / clock navigation, handled at the Activity level so it
     * cannot be swallowed by the Leanback grid's own key handling.
     * Chain: grid top row --Up--> gear --Down--> date --Down--> clock
     * --Down--> grid. Centre/Enter on the gear opens Settings.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (!settingsButton.hasFocus() && !dateText.hasFocus() &&
                        !clockText.hasFocus()
                    ) {
                        val focused = appGrid.focusedChild
                        val pos = if (focused != null) appGrid.getChildAdapterPosition(focused) else -1
                        Log.d(TAG, "DPAD_UP focusedPos=$pos")
                        if (pos in 0 until 4) {
                            val ok = settingsButton.requestFocus()
                            Log.d(TAG, "DPAD_UP top row -> gear requestFocus=$ok")
                            return true
                        }
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    when {
                        settingsButton.hasFocus() -> {
                            dateText.requestFocus()
                            Log.d(TAG, "DPAD_DOWN gear -> date")
                            return true
                        }
                        dateText.hasFocus() -> {
                            clockText.requestFocus()
                            Log.d(TAG, "DPAD_DOWN date -> clock")
                            return true
                        }
                        clockText.hasFocus() -> {
                            appGrid.getChildAt(0)?.requestFocus() ?: appGrid.requestFocus()
                            Log.d(TAG, "DPAD_DOWN clock -> grid")
                            return true
                        }
                    }
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (settingsButton.hasFocus()) {
                        Log.d(TAG, "CENTER on gear -> Settings")
                        settingsButton.performClick()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
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
            val visible = all.filter { it.packageName !in hidden }
            // Full custom order (Arrange apps screen); the old favorites
            // list migrates into it, and newly installed pinned apps
            // (Nuvio/SmartTube/Moonlight) join the top block.
            val order = AppsRepository.resolveOrder(visible, prefs)
            val byPkg = visible.associateBy { it.packageName }
            val ordered = order.mapNotNull { byPkg[it] }
            Log.d(TAG, "loadApps order: " + ordered.joinToString(", ") { it.label })
            runOnUiThread {
                adapter.submit(ordered)
                // Only grab focus if nothing has it. Forcing focus to the
                // top tile on every resume made the grid jump on each
                // Home press (v0.1.4).
                appGrid.post {
                    if (appGrid.focusedChild == null) {
                        appGrid.getChildAt(0)?.requestFocus()
                            ?: appGrid.requestFocus()
                    }
                }
            }
        }
    }

    private inner class AppAdapter(private val pm: PackageManager) :
        androidx.recyclerview.widget.RecyclerView.Adapter<AppAdapter.Holder>() {

        private var items: List<AppEntry> = emptyList()
        private var lastPackages: List<String> = emptyList()
        private var lastIconSig: List<String> = emptyList()
        private val iconCache = LruCache<String, Drawable>(48)

        /**
         * Icon identity for one app: package + custom icon URI + the icon
         * file's last-modified time. The picker overwrites the same file
         * (files/icons/<pkg>.jpg) on every pick, so the URI alone cannot
         * tell a new image from the old one (v0.1.9).
         */
        private fun iconKey(app: AppEntry): String {
            val customUri = prefs.customIcon(app.packageName)
            var mod = ""
            if (customUri != null) {
                try {
                    val path = Uri.parse(customUri).path
                    if (path != null) mod = File(path).lastModified().toString()
                } catch (e: Exception) { /* fall back to the URI alone */ }
            }
            return app.packageName + "|" + (customUri ?: "") + "|" + mod
        }

        fun submit(list: List<AppEntry>) {
            // NOTE (v0.1.5): skip the rebind when nothing changed — every
            // Home press re-submitted the same list, which flickered.
            // NOTE (v0.1.9): the skip must also compare the icons. A
            // changed custom icon leaves the order identical, so the old
            // check skipped the rebind and the new icon never showed.
            val pkgs = list.map { it.packageName }
            val sig = list.map { iconKey(it) }
            if (pkgs == lastPackages && sig == lastIconSig) {
                Log.d(TAG, "submit: order unchanged, skip")
                return
            }
            if (pkgs == lastPackages) Log.d(TAG, "submit: icons changed, rebind")
            lastPackages = pkgs
            lastIconSig = sig
            items = list
            notifyDataSetChanged()
        }

        /** Custom icon first (if the user picked one), else the app icon. */
        private fun loadIconDrawable(app: AppEntry): Drawable? {
            val customUri = prefs.customIcon(app.packageName)
            if (customUri != null) {
                try {
                    val bmp = decodeSampledBitmap(Uri.parse(customUri), 192, 192)
                    if (bmp != null) return BitmapDrawable(resources, bmp)
                } catch (e: Exception) { /* fall through to the app icon */ }
            }
            return try {
                pm.getApplicationIcon(app.packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                getDrawable(R.drawable.ic_zap)
            }
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
            // Cache key is the icon identity (v0.1.5, v0.1.9): a changed
            // icon refreshes by itself, so the cache is never evicted on
            // resume anymore — that eviction was the flicker.
            val iconKey = iconKey(app)
            val cached = iconCache.get(iconKey)
            if (cached != null) {
                h.icon.setImageDrawable(cached)
            } else {
                thread {
                    val drawable = loadIconDrawable(app)
                    if (drawable != null) iconCache.put(iconKey, drawable)
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

    companion object {
        const val TAG = "ZapLauncher"
    }
}
