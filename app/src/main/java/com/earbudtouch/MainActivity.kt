package com.earbudtouch

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.text.InputType

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var live: TextView
    private lateinit var list: LinearLayout
    private lateinit var sw: Switch
    private var updating = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float, bold: Boolean = false): TextView {
        val t = TextView(this); t.text = text; t.textSize = size
        if (bold) t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        t.setPadding(0, dp(6), 0, dp(6)); return t
    }

    private fun btn(text: String, f: () -> Unit): Button {
        val b = Button(this); b.text = text; b.setOnClickListener { f() }; return b
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val sc = ScrollView(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(18), dp(24), dp(18), dp(24))
        sc.addView(root); setContentView(sc)

        root.addView(tv("Earbud Touch", 26f, true))
        root.addView(tv("Use your earbuds' touch sensor as a custom remote: open apps or type letters with your own tap patterns.", 14f))

        sw = Switch(this); sw.text = "Earbuds control"
        sw.setOnCheckedChangeListener { _, on ->
            if (!updating) {
                if (on) startForegroundService(Intent(this, EarbudService::class.java))
                else stopService(Intent(this, EarbudService::class.java))
                sw.postDelayed({ refresh() }, 400)
            }
        }
        root.addView(sw)
        status = tv("", 13f); root.addView(status)
        root.addView(btn("Enable Accessibility (needed to open apps and type)") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        root.addView(btn("Timing settings") { showTiming() })

        root.addView(tv("Live monitor", 16f, true))
        live = tv("Tap your earbuds...", 15f); root.addView(live)

        root.addView(tv("Your gestures", 16f, true))
        list = LinearLayout(this); list.orientation = LinearLayout.VERTICAL; root.addView(list)
        root.addView(btn("+ Add gesture") { showAdd() })
        root.addView(tv("S = short tap, L = long press, N = double tap (earbuds send 'next'), P = triple tap (earbuds send 'previous'). Example: SLS = short, long, short.", 12f))

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onResume() {
        super.onResume()
        Bus.logger = { s -> live.text = s }
        refresh()
    }

    override fun onPause() { Bus.logger = null; super.onPause() }

    private fun refresh() {
        status.text = "Control service: " + (if (EarbudService.running) "ON" else "OFF") +
            "\nAccessibility (open apps + typing): " + (if (TypeService.instance != null) "ON" else "OFF")
        updating = true; sw.isChecked = EarbudService.running; updating = false
        list.removeAllViews()
        for (a in Store.load(this)) {
            val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
            val t = tv(a.p + "   ->   " + a.label, 15f)
            row.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(btn("X") {
                Store.save(this, Store.load(this).filter { it.p != a.p }); refresh()
            })
            list.addView(row)
        }
    }

    private fun showTiming() {
        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.setPadding(dp(20), dp(10), dp(20), 0)
        val l = EditText(this); l.inputType = InputType.TYPE_CLASS_NUMBER; l.setText(Store.longMs(this).toString())
        val i = EditText(this); i.inputType = InputType.TYPE_CLASS_NUMBER; i.setText(Store.idleMs(this).toString())
        val c = CheckBox(this); c.text = "A single short tap with no gesture = play/pause"; c.isChecked = Store.pass(this)
        box.addView(tv("Long press starts after (ms)", 13f)); box.addView(l)
        box.addView(tv("Pattern ends after silence of (ms)", 13f)); box.addView(i)
        box.addView(c)
        AlertDialog.Builder(this).setTitle("Timing").setView(box)
            .setPositiveButton("Save") { _, _ ->
                Store.setTiming(this, l.text.toString().toIntOrNull() ?: 450, i.text.toString().toIntOrNull() ?: 800, c.isChecked)
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showAdd() {
        val pm = packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }.sortedBy { it.first.lowercase() }
        val types = listOf("Open app", "Type text", "Backspace", "Media: play/pause", "Media: next", "Media: previous")

        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.setPadding(dp(20), dp(10), dp(20), 0)
        val pat = EditText(this); pat.hint = "Pattern, e.g. SLS (S L N P)"
        val rec = Button(this); rec.text = "Record from earbuds"
        val type = Spinner(this); type.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        val app = Spinner(this); app.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, apps.map { it.first })
        val txt = EditText(this); txt.hint = "Text to type (a letter, a word...)"; txt.visibility = View.GONE
        box.addView(pat); box.addView(rec); box.addView(type); box.addView(app); box.addView(txt)

        type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                app.visibility = if (pos == 0) View.VISIBLE else View.GONE
                txt.visibility = if (pos == 1) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        rec.setOnClickListener {
            if (!EarbudService.running) { Toast.makeText(this, "Turn on Earbuds control first", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            rec.text = "Now tap your earbuds..."
            Bus.recorder = { p -> pat.setText(p); rec.text = "Record from earbuds"; Bus.recorder = null }
        }
        val dlg = AlertDialog.Builder(this).setTitle("New gesture").setView(box)
            .setPositiveButton("Save") { _, _ ->
                val p = pat.text.toString().uppercase().filter { it in "SLNP" }
                if (p.isEmpty()) { Toast.makeText(this, "Pattern is empty", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                val act = when (type.selectedItemPosition) {
                    0 -> { val a = apps[app.selectedItemPosition]; Act(p, "APP", a.second, "Open " + a.first) }
                    1 -> Act(p, "TYPE", txt.text.toString(), "Type \"" + txt.text + "\"")
                    2 -> Act(p, "BACK", "", "Backspace")
                    3 -> Act(p, "MEDIA", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE.toString(), "Play/Pause")
                    4 -> Act(p, "MEDIA", KeyEvent.KEYCODE_MEDIA_NEXT.toString(), "Next track")
                    else -> Act(p, "MEDIA", KeyEvent.KEYCODE_MEDIA_PREVIOUS.toString(), "Previous track")
                }
                Store.save(this, Store.load(this).filter { it.p != p } + act)
                refresh()
            }.setNegativeButton("Cancel", null).create()
        dlg.setOnDismissListener { Bus.recorder = null }
        dlg.show()
    }
}
