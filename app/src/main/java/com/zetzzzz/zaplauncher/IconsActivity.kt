package com.zetzzzz.zaplauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread

/**
 * Change an app's home-grid icon to a custom image, or reset it (v0.1.3).
 *
 * NOTE (v0.1.5): the row itself is NOT focusable (see item_icon.xml).
 * A focusable row swallows D-pad focus, so the Change/Reset buttons
 * inside it could never be reached.
 *
 * NOTE (v0.1.6): the image is picked in Zap's own IconPickerActivity.
 * The system picker (GetContent) is only a stub on this TV that
 * instantly returns "cancelled".
 */
class IconsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var list: RecyclerView
    private var apps: List<AppEntry> = emptyList()
    private var pendingPkg: String? = null

    private val pickIcon = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK) {
            Log.d(TAG, "icon picker ok")
            refresh()
        } else {
            Log.d(TAG, "icon picker cancelled")
        }
    }

    private val requestStorage = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingPkg?.let { launchPicker(it) }
        } else {
            Toast.makeText(
                this, "Storage access is needed to pick an image", Toast.LENGTH_SHORT
            ).show()
        }
        pendingPkg = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        findViewById<TextView>(R.id.listTitle).text = getString(R.string.icons_title)
        findViewById<TextView>(R.id.listHint).text =
            "Pick an image as an app's icon. Reset restores the original icon."
        prefs = Prefs(this)
        list = findViewById(R.id.appList)
        list.layoutManager = LinearLayoutManager(this)
        thread {
            val loaded = AppsRepository.loadApps(packageManager, packageName)
            runOnUiThread {
                apps = loaded.filter { it.packageName !in prefs.hiddenApps() }
                refresh()
            }
        }
    }

    private fun storagePermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun launchPicker(pkg: String) {
        val intent = Intent(this, IconPickerActivity::class.java)
            .putExtra(IconPickerActivity.EXTRA_PKG, pkg)
        pickIcon.launch(intent)
    }

    private fun refresh() {
        list.adapter = IconsAdapter(apps, prefs,
            onChange = { pkg ->
                pendingPkg = pkg
                val perm = storagePermission()
                if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) {
                    launchPicker(pkg)
                } else {
                    requestStorage.launch(perm)
                }
            },
            onReset = { pkg ->
                prefs.setCustomIcon(pkg, null)
                Log.d(TAG, "icon reset: $pkg")
                refresh()
            }
        )
    }

    private class IconsAdapter(
        private val items: List<AppEntry>,
        private val prefs: Prefs,
        private val onChange: (String) -> Unit,
        private val onReset: (String) -> Unit
    ) : RecyclerView.Adapter<IconsAdapter.Holder>() {

        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val label: TextView = v.findViewById(R.id.rowTitle)
            val change: Button = v.findViewById(R.id.btnChange)
            val reset: Button = v.findViewById(R.id.btnReset)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_icon, parent, false)
            return Holder(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val app = items[position]
            val custom = prefs.customIcon(app.packageName)
            h.label.text = if (custom != null) "${app.label}  •  custom icon" else app.label
            h.reset.isEnabled = custom != null
            h.change.setOnClickListener { onChange(app.packageName) }
            h.reset.setOnClickListener { onReset(app.packageName) }
        }
    }

    companion object {
        const val TAG = "ZapLauncher"
    }
}
