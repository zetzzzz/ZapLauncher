package com.zetzzzz.zaplauncher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread

/** Pick which installed apps are hidden from the home grid. */
class HideAppsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var list: RecyclerView
    private var apps: List<AppEntry> = emptyList()
    private var hidden: MutableSet<String> = mutableSetOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        findViewById<TextView>(R.id.listTitle).text = getString(R.string.hide_apps_title)
        findViewById<TextView>(R.id.listHint).text =
            "Checked apps are hidden from the home grid. They stay installed."
        prefs = Prefs(this)
        hidden = prefs.hiddenApps().toMutableSet()
        list = findViewById(R.id.appList)
        list.layoutManager = LinearLayoutManager(this)
        thread {
            val loaded = AppsRepository.loadApps(packageManager, packageName)
            runOnUiThread {
                apps = loaded
                list.adapter = HideAdapter(apps, hidden) { pkg, isHidden ->
                    if (isHidden) hidden.add(pkg) else hidden.remove(pkg)
                    prefs.setHiddenApps(hidden)
                }
            }
        }
    }

    private class HideAdapter(
        private val items: List<AppEntry>,
        private val hidden: Set<String>,
        private val onToggle: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<HideAdapter.Holder>() {

        class Holder(v: View) : RecyclerView.ViewHolder(v) {
            val label: TextView = v.findViewById(R.id.rowTitle)
            val check: CheckBox = v.findViewById(R.id.rowCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_check_app, parent, false)
            return Holder(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: Holder, position: Int) {
            val app = items[position]
            h.label.text = app.label
            h.check.isChecked = app.packageName in hidden
            h.check.isFocusable = false
            h.itemView.setOnClickListener {
                val nowHidden = app.packageName !in hidden
                h.check.isChecked = nowHidden
                onToggle(app.packageName, nowHidden)
            }
        }
    }
}
