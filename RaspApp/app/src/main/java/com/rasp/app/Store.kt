package com.rasp.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.nio.charset.Charset
import java.time.LocalDate

data class Todo(val id: String, val title: String, val time: String?, val date: String, val sticky: Boolean)
data class Custom(val id: String, val title: String, val t1: String, val t2: String, val place: String, val kind: String, val date: String?, val wd: Int)

object Store {
    private fun p(c: Context) = c.getSharedPreferences("rasp", Context.MODE_PRIVATE)
    private fun newId() = System.currentTimeMillis().toString() + (0..999).random()

    // ---- ДЗ ----
    fun hw(c: Context): MutableMap<String, String> {
        val o = JSONObject(p(c).getString("hw", "{}") ?: "{}")
        val m = linkedMapOf<String, String>()
        for (k in o.keys()) m[k] = o.getString(k)
        return m
    }

    fun setHw(c: Context, key: String, value: String) {
        val m = hw(c)
        if (value.isEmpty()) m.remove(key) else m[key] = value
        val o = JSONObject()
        for ((k, v) in m) o.put(k, v)
        p(c).edit().putString("hw", o.toString()).apply()
    }

    // ---- Дела ----
    fun todos(c: Context): MutableList<Todo> {
        val a = JSONArray(p(c).getString("todos", "[]") ?: "[]")
        val out = mutableListOf<Todo>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(Todo(o.getString("id"), o.getString("title"), if (o.isNull("time")) null else o.getString("time"), o.getString("date"), o.getBoolean("sticky")))
        }
        return out
    }

    private fun saveTodos(c: Context, l: List<Todo>) {
        val a = JSONArray()
        for (t in l) a.put(JSONObject().put("id", t.id).put("title", t.title).put("time", t.time ?: JSONObject.NULL).put("date", t.date).put("sticky", t.sticky))
        p(c).edit().putString("todos", a.toString()).apply()
    }

    fun addTodo(c: Context, title: String, time: String?, date: String, sticky: Boolean) {
        val l = todos(c)
        l.add(Todo(newId(), title, time, date, sticky))
        saveTodos(c, l)
    }

    fun deleteTodo(c: Context, id: String) = saveTodos(c, todos(c).filter { it.id != id })

    fun todosFor(c: Context, d: LocalDate): List<Todo> =
        todos(c).filter { it.sticky || it.date == d.toString() }.sortedBy { it.time ?: "99:99" }

    // ---- Моё расписание ----
    fun custom(c: Context): MutableList<Custom> {
        val a = JSONArray(p(c).getString("custom", "[]") ?: "[]")
        val today = LocalDate.now().toString()
        val out = mutableListOf<Custom>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val date = if (o.isNull("date")) null else o.getString("date")
            if (date != null && date < today) continue
            out.add(Custom(o.getString("id"), o.getString("title"), o.getString("t1"), o.getString("t2"), o.getString("place"), o.getString("kind"), date, o.getInt("wd")))
        }
        return out
    }

    private fun saveCustom(c: Context, l: List<Custom>) {
        val a = JSONArray()
        for (x in l) a.put(JSONObject().put("id", x.id).put("title", x.title).put("t1", x.t1).put("t2", x.t2).put("place", x.place).put("kind", x.kind).put("date", x.date ?: JSONObject.NULL).put("wd", x.wd))
        p(c).edit().putString("custom", a.toString()).apply()
    }

    fun addCustom(c: Context, title: String, t1: String, t2: String, place: String, date: String?, wd: Int) {
        val l = custom(c)
        l.add(Custom(newId(), title, t1, t2, place, "Факультатив", date, wd))
        saveCustom(c, l)
    }

    fun deleteCustom(c: Context, id: String) = saveCustom(c, custom(c).filter { it.id != id })

    // ---- Мои пары как преподавателя ----
    fun teach(c: Context): String = p(c).getString("teach", Schedule.TEACH_DEFAULT) ?: Schedule.TEACH_DEFAULT
    fun setTeach(c: Context, s: String) = p(c).edit().putString("teach", s).apply()

    // ---- Кэш страницы ----
    private fun cacheFile(c: Context) = File(c.filesDir, "page.html")
    fun html(c: Context): String = try { cacheFile(c).readText() } catch (e: Exception) { "" }
    fun saveHtml(c: Context, s: String) = cacheFile(c).writeText(s)

    fun download(): String {
        val con = java.net.URL(Schedule.PAGE_URL).openConnection() as HttpURLConnection
        con.connectTimeout = 5000
        con.readTimeout = 6000
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
        val bytes = con.inputStream.use { it.readBytes() }
        val ct = (con.contentType ?: "").lowercase()
        val head = String(bytes, 0, minOf(bytes.size, 2000), Charsets.ISO_8859_1).lowercase()
        val cs: Charset = when {
            ct.contains("utf-8") -> Charsets.UTF_8
            ct.contains("1251") -> Charset.forName("windows-1251")
            head.contains("utf-8") -> Charsets.UTF_8
            else -> Charset.forName("windows-1251")
        }
        return String(bytes, cs)
    }
}
