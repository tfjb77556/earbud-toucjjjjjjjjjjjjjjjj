package com.earbudtouch

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Act(val p: String, val t: String, val v: String, val label: String)

object Bus {
    @Volatile var logger: ((String) -> Unit)? = null
    @Volatile var recorder: ((String) -> Unit)? = null
}

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun load(c: Context): MutableList<Act> {
        val out = mutableListOf<Act>()
        val a = JSONArray(sp(c).getString("maps", "[]"))
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(Act(o.getString("p"), o.getString("t"), o.optString("v"), o.optString("l")))
        }
        return out
    }

    fun save(c: Context, l: List<Act>) {
        val a = JSONArray()
        for (x in l) a.put(JSONObject().put("p", x.p).put("t", x.t).put("v", x.v).put("l", x.label))
        sp(c).edit().putString("maps", a.toString()).apply()
    }

    fun find(c: Context, p: String): Act? = load(c).firstOrNull { it.p == p }
    fun longMs(c: Context) = sp(c).getInt("long", 450)
    fun idleMs(c: Context) = sp(c).getInt("idle", 800)
    fun pass(c: Context) = sp(c).getBoolean("pass", true)
    fun setTiming(c: Context, longMs: Int, idleMs: Int, pass: Boolean) {
        sp(c).edit().putInt("long", longMs).putInt("idle", idleMs).putBoolean("pass", pass).apply()
    }
}
