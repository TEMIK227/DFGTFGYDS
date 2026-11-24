package com.rasp.app

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class Lesson(
    val date: LocalDate,
    val who: String,      // УЧУСЬ / ПРЕПОДАЮ / МОЁ
    val start: LocalTime,
    val end: LocalTime,
    val title: String,
    val place: String,
    val subject: String
) {
    val startAt: LocalDateTime get() = LocalDateTime.of(date, start)
    val endAt: LocalDateTime get() = LocalDateTime.of(date, end)
}

object Schedule {
    const val PAGE_URL = "https://kuzstu.ru/web-content/sitecontent/studentu/raspisanie/%D0%98%D0%98%D0%B1-263.html"
    const val TEACH_SUBJ = "Технология разработки ПО"

    // Формат строки: дата номер_пары тип аудитория группа [предмет]
    val TEACH_DEFAULT = """
2026-11-24 3 пр 5506 ИСт-243
2026-11-24 4 пр 5506 ИСт-241
2026-11-25 3 лаб 5514 ИСт-243
2026-11-25 5 пр 3304 ИСт-242
2026-11-30 1 пр 5514 ИСт-241
2026-11-30 2 пр 5514 ИСт-242
2026-11-30 3 пр 5514 ИСт-242
2026-12-01 1 пр 5514 ИСт-243
2026-12-07 4 конс 1337 ИСт-243
2026-12-09 5 конс 5514 ИСт-242
2026-12-11 3 конс 1337 ИСт-241
2026-12-14 1 конс 5514 ИСт-243
2026-12-14 2 конс 5514 ИСт-243
2026-12-16 3 конс 5514 ИСт-241
2026-12-18 1 конс 1337 ИСт-242
2026-12-18 2 конс 1337 ИСт-242
2026-12-18 3 конс 1311 ИСт-241
""".trimIndent()

    val PAIRS = mapOf(
        1 to Pair("09:00", "10:35"),
        2 to Pair("10:50", "12:25"),
        3 to Pair("13:20", "14:55"),
        4 to Pair("15:10", "16:45"),
        5 to Pair("17:00", "18:35")
    )

    private val CI = setOf(RegexOption.IGNORE_CASE)
    private val MS = listOf("январ", "феврал", "март", "апрел", "ма[яй]", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр")
    private val TIME_RANGE = Regex("(\\d{1,2})[:;.](\\d{2})\\s*[-–]\\s*(\\d{1,2})[:;.](\\d{2})")
    private val ROOM = Regex("^(.*?)\\s+(\\d{3,4}[а-яА-Я]?|\\d\\s*лекц\\.?)$")
    private val FIO = Regex("[А-ЯЁA-Z][а-яёa-z-]+\\s+[А-ЯЁA-Z]\\.\\s*[А-ЯЁA-Z]\\.?")
    private val KIND = Regex("^(?:лек|лекция|пр|практика|лаб|лабораторная|сем|фак|факультатив|доп|л)(?:\\.\\s*|\\s+)", CI)
    private val SUBGR_TAIL = Regex("\\s*-?\\s*[12]\\s*п/г\\s*$")
    private val META1 = Regex("^(пары|время|неделя)", CI)
    private val META2 = Regex("^\\d{1,2}\\s*-\\s*[яйе]\\s*$", CI)
    private val META3 = Regex("^(пнд|втр|срд|чтв|птн|сбт|вск)\\b", CI)

    private fun clean(s: String): String = s
        .replace(Regex("<br\\s*/?>", CI), " · ")
        .replace(Regex("<[^>]*>"), " ")
        .replace(Regex("&nbsp;", CI), " ")
        .replace("\u00A0", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace(Regex("\\s+"), " ")
        .replace(Regex("(\\s*·\\s*)+"), " · ")
        .replace(Regex("^\\s*·\\s*|\\s*·\\s*$"), "")
        .trim()

    private fun isMeta(s: String): Boolean {
        val t = s.trim()
        return t.isEmpty() || META1.containsMatchIn(t) || META2.containsMatchIn(t) || META3.containsMatchIn(t)
    }

    private fun tables(html: String): List<List<List<String>>> {
        val out = mutableListOf<List<List<String>>>()
        for (t in Regex("<table[^>]*>[\\s\\S]*?</table>", CI).findAll(html)) {
            val rows = mutableListOf<List<String>>()
            for (tr in Regex("<tr[^>]*>[\\s\\S]*?</tr>", CI).findAll(t.value)) {
                val cells = Regex("<t[dh][^>]*>[\\s\\S]*?</t[dh]>", CI).findAll(tr.value).map { clean(it.value) }.toList()
                if (cells.isNotEmpty()) rows.add(cells)
            }
            if (rows.size >= 2) out.add(rows)
        }
        return out
    }

    private fun matchDate(cell: String, d: LocalDate): Boolean {
        val s = cell.lowercase()
        if (s.isEmpty()) return false
        val day = d.dayOfMonth
        val mo = d.monthValue
        val hasDay = Regex("(^|[^0-9])0?" + day + "([^0-9]|$)").containsMatchIn(s)
        val num = Regex("(^|[^0-9])0?" + day + "[./-]0?" + mo + "([^0-9]|$)").containsMatchIn(s)
        return (hasDay && Regex(MS[mo - 1], CI).containsMatchIn(s)) || num
    }

    // Подгруппа 2: оставляем только строки "2 п/г" и без пометки
    private fun subgroup(info: String): String {
        val s = info.trim()
        val any = Regex("[12]\\s*п/г", CI)
        val two = Regex("2\\s*п/г", CI)
        val one = Regex("1\\s*п/г", CI)
        if (!any.containsMatchIn(s)) return s
        if (two.containsMatchIn(s) && !one.containsMatchIn(s)) return s
        return s.split(" · ").filter { two.containsMatchIn(it) }.joinToString(" · ")
    }

    private fun noFio(info: String): String =
        info.split(" · ")
            .map { FIO.replace(it, " ").replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" · ")

    fun subjectKey(title: String): String =
        SUBGR_TAIL.replace(KIND.replace(title.trim(), ""), "").trim().trimEnd('-').trim()

    private fun studentDay(tabs: List<List<List<String>>>, d: LocalDate): List<Lesson> {
        for (rows in tabs) {
            val tr = rows.firstOrNull { r -> r.any { TIME_RANGE.containsMatchIn(it) } } ?: continue
            val dr = rows.firstOrNull { matchDate(it[0], d) } ?: continue
            val out = mutableListOf<Lesson>()
            for (i in 1 until maxOf(tr.size, dr.size)) {
                val raw = dr.getOrElse(i) { "" }.trim()
                val m = TIME_RANGE.find(tr.getOrElse(i) { "" }) ?: continue
                if (raw.isEmpty() || raw == "-" || raw == "—" || isMeta(raw)) continue
                val info = subgroup(raw)
                if (info.isEmpty() || isMeta(info)) continue
                val parts = noFio(info).split(" · ")
                val first = parts[0]
                val rm = ROOM.find(first)
                val title = rm?.groupValues?.get(1) ?: first
                var place = rm?.groupValues?.get(2)?.let { "ауд. $it" } ?: ""
                val rest = parts.drop(1).joinToString(" · ")
                if (rest.isNotEmpty()) place = if (place.isEmpty()) rest else "$place · $rest"
                out.add(
                    Lesson(
                        d, "УЧУСЬ",
                        LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()),
                        LocalTime.of(m.groupValues[3].toInt(), m.groupValues[4].toInt()),
                        title, place, subjectKey(title)
                    )
                )
            }
            return out
        }
        return emptyList()
    }

    fun teachLessons(text: String): List<Lesson> = text.lines().mapNotNull { line ->
        val p = line.trim().split(Regex("\\s+"))
        if (p.size < 5) return@mapNotNull null
        try {
            val d = LocalDate.parse(p[0])
            val pr = PAIRS[p[1].toInt()] ?: return@mapNotNull null
            val subj = if (p.size > 5) p.drop(5).joinToString(" ") else TEACH_SUBJ
            Lesson(d, "ПРЕПОДАЮ", LocalTime.parse(pr.first), LocalTime.parse(pr.second), p[2] + ". " + subj, "ауд. " + p[3] + " · " + p[4], subj)
        } catch (e: Exception) {
            null
        }
    }

    fun parseRange(s: String): Pair<String, String>? {
        val m = TIME_RANGE.find(s) ?: return null
        fun f(h: String, mi: String) = h.padStart(2, '0') + ":" + mi
        return Pair(f(m.groupValues[1], m.groupValues[2]), f(m.groupValues[3], m.groupValues[4]))
    }

    fun parseTime(s: String): String? {
        val m = Regex("(\\d{1,2})[:;.](\\d{2})").find(s) ?: return null
        return m.groupValues[1].padStart(2, '0') + ":" + m.groupValues[2]
    }

    fun upcoming(html: String, teachText: String, custom: List<Custom>, now: LocalDateTime): List<Lesson> {
        val tabs = tables(html)
        val out = mutableListOf<Lesson>()
        val today = now.toLocalDate()
        for (i in 0..2) out.addAll(studentDay(tabs, today.plusDays(i.toLong())))
        val last = today.plusDays(2)
        out.addAll(teachLessons(teachText).filter { it.date >= today && it.date <= last })
        for (i in 0..2) {
            val d = today.plusDays(i.toLong())
            for (c in custom) {
                val hit = if (c.date != null) c.date == d.toString() else c.wd == d.dayOfWeek.value
                if (hit) {
                    out.add(
                        Lesson(d, "МОЁ", LocalTime.parse(c.t1), LocalTime.parse(c.t2), c.title,
                            if (c.place.isEmpty()) "" else "ауд. " + c.place, c.title)
                    )
                }
            }
        }
        return out.filter { it.endAt > now }.sortedBy { it.startAt }
    }

    fun subjects(html: String, custom: List<Custom>): List<String> {
        val set = sortedSetOf<String>()
        set.add(TEACH_SUBJ)
        for (rows in tables(html)) {
            val tr = rows.firstOrNull { r -> r.any { TIME_RANGE.containsMatchIn(it) } } ?: continue
            for (r in rows) {
                if (r === tr || isMeta(r[0])) continue
                for (i in 1 until r.size) {
                    val raw = r[i].trim()
                    if (raw.isEmpty() || raw == "-" || raw == "—" || isMeta(raw)) continue
                    val info = subgroup(raw)
                    if (info.isEmpty() || isMeta(info)) continue
                    val first = noFio(info).split(" · ")[0]
                    val t = ROOM.find(first)?.groupValues?.get(1) ?: first
                    val k = subjectKey(t)
                    if (k.isNotEmpty() && !isMeta(k)) set.add(k)
                }
            }
        }
        custom.forEach { set.add(it.title) }
        return set.toList()
    }
}
