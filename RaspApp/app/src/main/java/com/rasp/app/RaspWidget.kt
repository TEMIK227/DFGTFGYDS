package com.rasp.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class RaspWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        Thread {
            try {
                Updater.refresh(context, true)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

class RaspWidgetSmall : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        Thread {
            try {
                Updater.refresh(context, true)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

object Updater {
    private val HM = DateTimeFormatter.ofPattern("HH:mm")

    fun dayWord(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "Сегодня"
        today.plusDays(1) -> "Завтра"
        else -> d.format(DateTimeFormatter.ofPattern("dd.MM"))
    }

    fun color(who: String): Int = when (who) {
        "ПРЕПОДАЮ" -> Color.parseColor("#FFA94D")
        "МОЁ" -> Color.parseColor("#8CE99A")
        else -> Color.parseColor("#8AB4FF")
    }

    fun refresh(ctx: Context, fetch: Boolean) {
        val app = ctx.applicationContext
        if (fetch) {
            try {
                Store.saveHtml(app, Store.download())
            } catch (e: Exception) {
            }
        }
        val now = LocalDateTime.now()
        val list = Schedule.upcoming(Store.html(app), Store.teach(app), Store.custom(app), now)
        val mgr = AppWidgetManager.getInstance(app)
        val big = mgr.getAppWidgetIds(ComponentName(app, RaspWidget::class.java))
        val small = mgr.getAppWidgetIds(ComponentName(app, RaspWidgetSmall::class.java))
        if (big.isEmpty() && small.isEmpty()) return
        if (big.isNotEmpty()) mgr.updateAppWidget(big, build(app, list, now))
        if (small.isNotEmpty()) mgr.updateAppWidget(small, buildSmall(app, list, now))
        if (big.isNotEmpty()) schedule(app, list, now, RaspWidget::class.java, big)
        else schedule(app, list, now, RaspWidgetSmall::class.java, small)
    }

    private fun buildSmall(app: Context, list: List<Lesson>, now: LocalDateTime): RemoteViews {
        val rv = RemoteViews(app.packageName, R.layout.widget_small)
        val today = now.toLocalDate()
        val c = list.firstOrNull()
        fun show(id: Int, text: String) {
            rv.setTextViewText(id, text)
            rv.setViewVisibility(id, if (text.isEmpty()) View.GONE else View.VISIBLE)
        }
        if (c != null) {
            val day = if (c.startAt <= now) "Идёт сейчас" else dayWord(c.date, today)
            rv.setTextViewText(R.id.s_tag, c.who + " · " + day)
            rv.setTextColor(R.id.s_tag, color(c.who))
            rv.setTextViewText(R.id.s_time, c.start.format(HM) + "–" + c.end.format(HM))
            show(R.id.s_title, c.title)
            val h = Store.hw(app)[c.subject]
            show(R.id.s_hw, if (h.isNullOrEmpty()) "" else "ДЗ: $h")
        } else {
            rv.setTextViewText(R.id.s_tag, "РАСПИСАНИЕ")
            rv.setTextColor(R.id.s_tag, Color.parseColor("#7AA2F7"))
            rv.setTextViewText(R.id.s_time, "Пар нет")
            show(R.id.s_title, "")
            show(R.id.s_hw, "")
        }
        val n = Store.todosFor(app, today).size
        show(R.id.s_todo, if (n == 0) "Дел нет" else "Дел: $n")
        val open = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.s_root, open)
        return rv
    }

    private fun build(app: Context, list: List<Lesson>, now: LocalDateTime): RemoteViews {
        val rv = RemoteViews(app.packageName, R.layout.widget)
        val today = now.toLocalDate()
        val c = list.firstOrNull()
        val n = list.getOrNull(1)
        fun show(id: Int, text: String) {
            rv.setTextViewText(id, text)
            rv.setViewVisibility(id, if (text.isEmpty()) View.GONE else View.VISIBLE)
        }
        if (c != null) {
            val day = if (c.startAt <= now) "Идёт сейчас" else dayWord(c.date, today)
            rv.setTextViewText(R.id.w_tag, c.who + " · " + day)
            rv.setTextColor(R.id.w_tag, color(c.who))
            rv.setTextViewText(R.id.w_time, c.start.format(HM) + "–" + c.end.format(HM))
            show(R.id.w_title, c.title)
            show(R.id.w_place, c.place)
            val h = Store.hw(app)[c.subject]
            show(R.id.w_hw, if (h.isNullOrEmpty()) "" else "ДЗ: $h")
            show(R.id.w_after, if (n == null) "" else "Потом: " + (if (n.date == c.date) "" else dayWord(n.date, today) + " ") + n.start.format(HM) + " · " + n.title)
        } else {
            rv.setTextViewText(R.id.w_tag, "РАСПИСАНИЕ")
            rv.setTextColor(R.id.w_tag, Color.parseColor("#7AA2F7"))
            rv.setTextViewText(R.id.w_time, "Пар нет")
            show(R.id.w_title, "")
            show(R.id.w_place, "")
            show(R.id.w_hw, "")
            show(R.id.w_after, "")
        }
        val t = Store.todosFor(app, today)
        show(
            R.id.w_todos,
            if (t.isEmpty()) "На сегодня дел нет"
            else t.take(3).joinToString("\n") { "• " + (if (it.time != null) it.time + " " else "") + it.title }
        )
        val open = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.w_root, open)
        return rv
    }

    private fun schedule(app: Context, list: List<Lesson>, now: LocalDateTime, cls: Class<*>, ids: IntArray) {
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(app, cls)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        val pi = PendingIntent.getBroadcast(app, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        var next = now.plusMinutes(15)
        list.firstOrNull()?.let {
            val e = it.endAt.plusMinutes(1)
            if (e > now && e < next) next = e
        }
        am.set(AlarmManager.RTC_WAKEUP, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
    }
}
