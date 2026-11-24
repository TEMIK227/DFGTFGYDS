package com.rasp.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private val WD = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sv = ScrollView(this)
        sv.setBackgroundColor(Color.parseColor("#121316"))
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(40), dp(16), dp(40))
        sv.addView(root)
        setContentView(sv)
    }

    override fun onResume() {
        super.onResume()
        render()
        Thread {
            Updater.refresh(this, true)
            runOnUiThread { render() }
        }.start()
    }

    private fun changed() {
        Thread { Updater.refresh(this, false) }.start()
        render()
    }

    private fun text(s: String, size: Float, color: String, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(Color.parseColor(color))
        if (bold) t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        t.setPadding(0, dp(4), 0, dp(4))
        return t
    }

    private fun header(s: String) {
        val t = text(s, 18f, "#FFFFFF", true)
        t.setPadding(0, dp(22), 0, dp(6))
        root.addView(t)
    }

    private fun button(s: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = s
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        return b
    }

    private fun rowWithDelete(label: String, onDelete: () -> Unit): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        val t = text(label, 15f, "#E0E4F5")
        r.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(button("🗑") { onDelete() })
        return r
    }

    private fun ask(title: String, hints: List<String>, init: List<String>, extra: View?, labels: List<String>, cb: (Int, List<String>) -> Unit) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val fields = hints.mapIndexed { i, h ->
            val e = EditText(this)
            e.hint = h
            e.setText(init.getOrElse(i) { "" })
            box.addView(e)
            e
        }
        if (extra != null) box.addView(extra)
        val b = AlertDialog.Builder(this).setTitle(title).setView(box)
        fun vals() = fields.map { it.text.toString().trim() }
        b.setPositiveButton(labels[0]) { _, _ -> cb(0, vals()) }
        if (labels.size > 1) b.setNeutralButton(labels[1]) { _, _ -> cb(1, vals()) }
        b.setNegativeButton("Отмена", null)
        b.show()
    }

    private fun render() {
        root.removeAllViews()
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val html = Store.html(this)
        val custom = Store.custom(this)
        val list = Schedule.upcoming(html, Store.teach(this), custom, now)
        val hwMap = Store.hw(this)

        root.addView(text("Мои пары", 26f, "#FFFFFF", true))

        // Следующая пара
        val c = list.firstOrNull()
        if (c != null) {
            val hm = DateTimeFormatter.ofPattern("HH:mm")
            val day = if (c.startAt <= now) "Идёт сейчас" else Updater.dayWord(c.date, today)
            root.addView(text(c.who + " · " + day, 13f, String.format("#%06X", 0xFFFFFF and Updater.color(c.who)), true))
            root.addView(text(c.start.format(hm) + "–" + c.end.format(hm), 30f, "#FFFFFF", true))
            root.addView(text(c.title, 16f, "#FFFFFF", true))
            if (c.place.isNotEmpty()) root.addView(text(c.place, 13f, "#AEB4D0"))
            val h = hwMap[c.subject]
            if (!h.isNullOrEmpty()) root.addView(text("ДЗ: $h", 13f, "#F9E2AF"))
        } else {
            root.addView(text(if (html.isEmpty()) "Расписание ещё не загружено (нужен интернет)" else "Ближайших пар нет", 15f, "#AEB4D0"))
        }
        root.addView(button("Обновить расписание с сайта") {
            Thread {
                Updater.refresh(this, true)
                runOnUiThread { render() }
            }.start()
        })

        // Дела
        header("Мои дела")
        val todos = Store.todos(this).sortedWith(compareBy<Todo>({ it.sticky.not() }, { it.date }, { it.time ?: "99:99" }))
        if (todos.isEmpty()) root.addView(text("Дел нет", 14f, "#6C7086"))
        for (t in todos) {
            val label = (if (t.sticky) "📌 " else t.date.substring(5) + " ") + (if (t.time != null) t.time + " " else "") + t.title
            root.addView(rowWithDelete(label) { Store.deleteTodo(this, t.id); changed() })
        }
        root.addView(button("+ Добавить дело") {
            ask("Новое дело", listOf("Что сделать", "Время (необязательно, 15:00)", "Дата ГГГГ-ММ-ДД"), listOf("", "", today.toString()), null,
                listOf("На этот день", "📌 Постоянное")) { which, v ->
                if (v[0].isNotEmpty()) {
                    val date = try { LocalDate.parse(v[2]).toString() } catch (e: Exception) { today.toString() }
                    Store.addTodo(this, v[0], Schedule.parseTime(v[1]), date, which == 1)
                    changed()
                }
            }
        })

        // ДЗ
        header("Домашнее задание")
        for (s in Schedule.subjects(html, custom)) {
            val cur = hwMap[s] ?: ""
            root.addView(button(if (cur.isEmpty()) s else "$s ✓  $cur") {
                ask(s, listOf("Например: № 145, 148 стр. 32"), listOf(cur), null, listOf("Сохранить")) { _, v ->
                    Store.setHw(this, s, v[0])
                    changed()
                }
            })
        }

        // Моё расписание
        header("Моё расписание (факультативы)")
        if (custom.isEmpty()) root.addView(text("Пока ничего", 14f, "#6C7086"))
        for (x in custom) {
            val when_ = if (x.date != null) "Один раз " + x.date else "Каждый " + WD[x.wd - 1]
            root.addView(rowWithDelete(when_ + " · " + x.t1 + "–" + x.t2 + " · " + x.title) { Store.deleteCustom(this, x.id); changed() })
        }
        root.addView(button("+ Добавить занятие") {
            val sp = Spinner(this)
            sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, WD)
            ask("Новое занятие", listOf("Название", "Время (17:00-18:35)", "Аудитория (необязательно)", "Дата ГГГГ-ММ-ДД (для «Один раз»)"),
                listOf("", "17:00-18:35", "", today.toString()), sp, listOf("Каждую неделю (день выше)", "Один раз (по дате)")) { which, v ->
                val range = Schedule.parseRange(v[1])
                if (v[0].isNotEmpty() && range != null) {
                    val date = if (which == 1) (try { LocalDate.parse(v[3]).toString() } catch (e: Exception) { null }) else null
                    if (which == 0 || date != null) {
                        Store.addCustom(this, v[0], range.first, range.second, v[2], date, sp.selectedItemPosition + 1)
                        changed()
                    }
                }
            }
        })

        // Преподаю
        header("Преподаю (мои пары)")
        root.addView(text("Строка: дата номер_пары тип аудитория группа. Например: 2026-12-01 3 пр 5514 ИСт-243", 12f, "#6C7086"))
        root.addView(button("Редактировать список") {
            val e = EditText(this)
            e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            e.setText(Store.teach(this))
            e.setLines(12)
            e.gravity = android.view.Gravity.TOP
            AlertDialog.Builder(this).setTitle("Мои пары как преподавателя").setView(e)
                .setPositiveButton("Сохранить") { _, _ -> Store.setTeach(this, e.text.toString()); changed() }
                .setNeutralButton("Сбросить") { _, _ -> Store.setTeach(this, Schedule.TEACH_DEFAULT); changed() }
                .setNegativeButton("Отмена", null).show()
        })
    }
}
