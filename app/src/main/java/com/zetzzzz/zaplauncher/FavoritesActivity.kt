package com.zetzzzz.zaplauncher

import android.os.Bundle
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
 * Reorder the pinned favorite apps (shown first on the home grid) and add
 * or remove favorites. Order is saved and applied on the home screen.
 */
class FavoritesActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var list: RecyclerView
    private var apps: List<AppEntry> = emptyList()
    private var favorites: MutableList<String> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        findViewById<TextView>(R.id.listTitle).text = getString(R.string.favorites_title)
        findViewById<TextView>(R.id.listHint).text =
            "Favorites appear first on the home grid, in this order."
        prefs = Prefs(this)
        favorites = prefs.favorites().toMutableList()
        list = findViewById(R.id.appList)
        list.layoutManager = LinearLayoutManager(this)
        thread {
            val loaded = AppsRepository.loadApps(packageManager, packageName)
            runOnUiThread {
                apps = loaded
                if (favorites.isEmpty()) {
                    favorites = AppsRepository.defaultFavorites(apps).toMutableList()
                    prefs.setFavorites(favorites)
                }
                refresh()
            }
        }
    }

    private fun refresh() {
        list.adapter = FavoritesAdapter(apps, favorites,
            onMove = { from, to ->
                val item = favorites.removeAt(from)
                favorites.add(to, item)
                prefs.setFavorites(favorites)
                refresh()
            },
            onRemove = { pkg ->
                favorites.remove(pkg)
                prefs.setFavorites(favorites)
                refresh()
            },
            onAdd = { pkg ->
                favorites.add(pkg)
                prefs.setFavorites(favorites)
                refresh()
            }
        )
    }

    private sealed class Row {
        data class Header(val text: String) : Row()
        data class Favorite(val app: AppEntry, val index: Int) : Row()
        data class Addable(val app: AppEntry) : Row()
    }

    private class FavoritesAdapter(
        apps: List<AppEntry>,
        favorites: List<String>,
        private val onMove: (Int, Int) -> Unit,
        private val onRemove: (String) -> Unit,
        private val onAdd: (String) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val rows: List<Row> = buildList {
            val byPkg = apps.associateBy { it.packageName }
            add(Row.Header("Favorites — shown first"))
            favorites.forEachIndexed { i, pkg ->
                byPkg[pkg]?.let { add(Row.Favorite(it, i)) }
            }
            add(Row.Header("All apps — tap + to pin"))
            apps.filter { it.packageName !in favorites }
                .forEach { add(Row.Addable(it)) }
        }

        class HeaderHolder(v: View) : RecyclerView.ViewHolder(v) {
            val text: TextView = v.findViewById(R.id.rowTitle)
        }
        class FavHolder(v: View) : RecyclerView.ViewHolder(v) {
            val label: TextView = v.findViewById(R.id.rowTitle)
            val up: Button = v.findViewById(R.id.btnUp)
            val down: Button = v.findViewById(R.id.btnDown)
            val remove: Button = v.findViewById(R.id.btnRemove)
        }
        class AddHolder(v: View) : RecyclerView.ViewHolder(v) {
            val label: TextView = v.findViewById(R.id.rowTitle)
            val add: Button = v.findViewById(R.id.btnAdd)
        }

        override fun getItemViewType(position: Int) = when (rows[position]) {
            is Row.Header -> 0
            is Row.Favorite -> 1
            is Row.Addable -> 2
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return when (viewType) {
                0 -> HeaderHolder(inf.inflate(R.layout.item_header, parent, false))
                1 -> FavHolder(inf.inflate(R.layout.item_favorite, parent, false))
                else -> AddHolder(inf.inflate(R.layout.item_addable, parent, false))
            }
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Header -> (h as HeaderHolder).text.text = row.text
                is Row.Favorite -> {
                    val holder = h as FavHolder
                    holder.label.text = row.app.label
                    holder.up.isEnabled = row.index > 0
                    holder.down.isEnabled = row.index < favoritesCount() - 1
                    holder.up.setOnClickListener { onMove(row.index, row.index - 1) }
                    holder.down.setOnClickListener { onMove(row.index, row.index + 1) }
                    holder.remove.setOnClickListener { onRemove(row.app.packageName) }
                }
                is Row.Addable -> {
                    val holder = h as AddHolder
                    holder.label.text = row.app.label
                    holder.add.setOnClickListener { onAdd(row.app.packageName) }
                }
            }
        }

        private fun favoritesCount() = rows.count { it is Row.Favorite }
    }
}
