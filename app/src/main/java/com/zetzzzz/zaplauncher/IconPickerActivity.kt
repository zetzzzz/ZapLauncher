package com.zetzzzz.zaplauncher

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * Built-in image picker (v0.1.6): a file browser, not a photo grid.
 *
 * v0.1.6 listed the TV's photos via MediaStore, but MediaStore returns
 * nothing on this TV — the picker opened empty. The system image picker
 * is also only a stub (DocumentsStub) that instantly cancels. So Zap
 * now browses the real folders itself: folders first, then image files.
 * The chosen image is downscaled and copied into the app's private
 * storage, so the icon survives reboots and permission changes.
 *
 * Navigation: click a folder to enter it, Back goes up one level (Back
 * at the root closes the picker). The row itself is the focusable item
 * (like the Settings rows) — no buttons inside it, so D-pad focus
 * cannot get trapped.
 */
class IconPickerActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var list: RecyclerView
    private lateinit var hint: TextView
    private var pkg: String = ""
    private var currentDir: File = storageRoot()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        pkg = intent.getStringExtra(EXTRA_PKG) ?: ""
        findViewById<TextView>(R.id.listTitle).text = "Choose an image"
        hint = findViewById(R.id.listHint)
        prefs = Prefs(this)
        list = findViewById(R.id.appList)
        list.layoutManager = LinearLayoutManager(this)
        openDir(currentDir)
    }

    override fun onResume() {
        super.onResume()
        // Focus guard (v0.1.7): the list is filled from a background
        // thread, after the window's initial focus pass.
        list.post {
            if (list.focusedChild == null) {
                list.getChildAt(0)?.requestFocus()
            }
        }
    }

    /** Back goes up one folder; Back at the root closes the picker. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val parent = currentDir.parentFile
        if (parent != null && currentDir != storageRoot()) {
            openDir(parent)
        } else {
            super.onBackPressed()
        }
    }

    private fun storageRoot(): File {
        val ext = Environment.getExternalStorageDirectory()
        return if (ext != null && ext.isDirectory && ext.canRead()) ext
        else File("/sdcard")
    }

    private fun openDir(dir: File) {
        currentDir = dir
        hint.text = dir.absolutePath
        thread {
            val entries = listEntries(dir)
            runOnUiThread {
                if (entries.isEmpty()) {
                    hint.text = dir.absolutePath + "\nFolder ini kosong."
                }
                Log.d(TAG, "picker dir: ${dir.absolutePath} (${entries.size} items)")
                list.adapter = FileAdapter(entries,
                    onFolder = { openDir(it) },
                    onFile = { onPicked(it) }
                )
                // Focus the first row explicitly (v0.1.7).
                list.post {
                    if (list.focusedChild == null) {
                        list.getChildAt(0)?.requestFocus()
                    }
                }
            }
        }
    }

    /** Folders first (A-Z), then image files (A-Z). Hidden files skipped. */
    private fun listEntries(dir: File): List<File> {
        val all = try {
            dir.listFiles()?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.d(TAG, "listEntries failed: ${e.message}")
            emptyList()
        }
        val folders = all.filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase() }
        val images = all.filter { it.isFile && isImage(it) && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase() }
        return folders + images
    }

    private fun isImage(f: File): Boolean {
        val name = f.name.lowercase()
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
            name.endsWith(".png") || name.endsWith(".webp") ||
            name.endsWith(".gif") || name.endsWith(".bmp")
    }

    private fun onPicked(file: File) {
        thread {
            val saved = savePickedImage(file, pkg)
            runOnUiThread {
                if (saved != null) {
                    prefs.setCustomIcon(pkg, saved.toString())
                    Log.d(TAG, "icon saved: $pkg -> $saved")
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this, "Could not use this image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Downscale to 512px and copy into private storage. */
    private fun savePickedImage(source: File, pkg: String): Uri? {
        return try {
            val bmp = decodeSampledFile(source, 512, 512) ?: return null
            val dir = File(filesDir, "icons").apply { mkdirs() }
            val file = File(dir, pkg.replace(".", "_") + ".jpg")
            FileOutputStream(file).use {
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.d(TAG, "savePickedImage failed: ${e.message}")
            null
        }
    }

    private fun decodeSampledFile(file: File, reqW: Int, reqH: Int) =
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > reqW || bounds.outHeight / sample > reqH) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (e: Exception) {
            null
        }

    private class FileAdapter(
        private val items: List<File>,
        private val onFolder: (File) -> Unit,
        private val onFile: (File) -> Unit
    ) : RecyclerView.Adapter<FileAdapter.Holder>() {

        private val thumbs = LruCache<String, Drawable>(64)

        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val thumb: ImageView = v.findViewById(R.id.fileThumb)
            val label: TextView = v.findViewById(R.id.fileName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file, parent, false)
            return Holder(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val file = items[position]
            h.label.text = file.name
            if (file.isDirectory) {
                h.thumb.setImageResource(R.drawable.ic_folder)
                h.itemView.setOnClickListener { onFolder(file) }
            } else {
                val key = file.absolutePath
                val cached = thumbs.get(key)
                if (cached != null) {
                    h.thumb.setImageDrawable(cached)
                } else {
                    h.thumb.setImageDrawable(null)
                    thread {
                        val bmp = decodeThumb(file)
                        if (bmp != null) {
                            val d = BitmapDrawable(h.thumb.resources, bmp)
                            thumbs.put(key, d)
                            h.thumb.post {
                                @Suppress("DEPRECATION")
                                if (h.adapterPosition == position) h.thumb.setImageDrawable(d)
                            }
                        }
                    }
                }
                h.itemView.setOnClickListener { onFile(file) }
            }
        }

        private fun decodeThumb(file: File) =
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 96 || bounds.outHeight / sample > 96) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(file.absolutePath, opts)
            } catch (e: Exception) {
                null
            }
    }

    companion object {
        const val EXTRA_PKG = "pkg"
        const val TAG = "ZapLauncher"
    }
}
