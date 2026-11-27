package com.zetzzzz.zaplauncher

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread

/**
 * Arrange the home grid (v0.1.3): every visible app in display order.
 * Move apps with Up/Down, hide an app with the ✕ button (unhide it in
 * Hidden apps). The order is saved and applied on the home screen.
 * (Replaces the old favorites-only ordering.)
 *
 * NOTE (v0.1.5): the row itself is NOT focusable (see item_favorite.xml).
 * A focusable row swallows D-pad focus, so the ↑ ↓ ✕ buttons inside it
 * could never be reached — focus search cannot move from a container
 * onto a view inside its own bounds.
 */
class FavoritesActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var list: RecyclerView
    private var order: MutableList<String> = mutableListOf()
    private var apps: List<AppEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        findViewById<TextView>(R.id.listTitle).text = getString(R.string.arrange_title)
        findViewById<TextView>(R.id.listHint).text =
            "Move apps with ↑ ↓. The top of the list is the top of the home grid. ✕ hides an app."
        prefs = Prefs(this)
        list = findViewById(R.id.appList)
        list.layoutManager = LinearLayoutManager(this)
        thread {
            val loaded = AppsRepository.loadApps(packageManager, packageName)
            val visible = loaded.filter { it.packageName !in prefs.hiddenApps() }
            val resolved = AppsRepository.resolveOrder(visible, prefs)
            runOnUiThread {
                apps = visible
                order = resolved.toMutableList()
                refresh()
            }
        }
    }

    private fun refresh() {
        list.adapter = ArrangeAdapter(apps, order,
            onMove = { from, to ->
                val item = order.removeAt(from)
                order.add(to, item)
                prefs.setAppOrder(order)
                Log.d(TAG, "arrange move: ${item} $from -> $to")
                refresh()
            },
            onHide = { pkg ->
                val hidden = prefs.hiddenApps().toMutableSet()
                hidden.add(pkg)
                prefs.setHiddenApps(hidden)
                order.remove(pkg)
                prefs.setAppOrder(order)
                Log.d(TAG, "arrange hide: $pkg")
                refresh()
            }
        )
    }

    private class ArrangeAdapter(
        apps: List<AppEntry>,
        private val order: List<String>,
        private val onMove: (Int, Int) -> Unit,
        private val onHide: (String) -> Unit
    ) : RecyclerView.Adapter<ArrangeAdapter.Holder>() {

        private val rows: List<AppEntry> =
            order.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }

        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val label: TextView = v.findViewById(R.id.rowTitle)
            val up: Button = v.findViewById(R.id.btnUp)
            val down: Button = v.findViewById(R.id.btnDown)
            val remove: Button = v.findViewById(R.id.btnRemove)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_favorite, parent, false)
            return Holder(v)
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val app = rows[position]
            h.label.text = app.label
            h.up.isEnabled = position > 0
            h.down.isEnabled = position < rows.size - 1
            h.up.setOnClickListener { onMove(position, position - 1) }
            h.down.setOnClickListener { onMove(position, position + 1) }
            h.remove.setOnClickListener { onHide(app.packageName) }
        }
    }

    companion object {
        const val TAG = "ZapLauncher"
    }
}
