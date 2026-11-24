package com.example.timelog

import android.app.*
import android.content.*
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import java.text.SimpleDateFormat
import java.util.*

const val HALF = 30 * 60 * 1000L

/** 对齐到所在的半小时起点（毫秒） */
fun slotOf(t: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    c.set(Calendar.MINUTE, if (c.get(Calendar.MINUTE) >= 30) 30 else 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** offset=0 今天0点，1 明天0点，-1 昨天0点 */
fun dayStart(offset: Int): Long {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, offset)
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun hhmm(t: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))

data class Rec(val slot: Long, val tag: String, val note: String)

class Db(c: Context) : SQLiteOpenHelper(c.applicationContext, "log.db", null, 1) {
    override fun onCreate(d: SQLiteDatabase) {
        d.execSQL("CREATE TABLE r(slot INTEGER PRIMARY KEY, tag TEXT NOT NULL, note TEXT NOT NULL DEFAULT '')")
    }

    override fun onUpgrade(d: SQLiteDatabase, o: Int, n: Int) {}

    fun put(slot: Long, tag: String, note: String) {
        val v = ContentValues()
        v.put("slot", slot); v.put("tag", tag); v.put("note", note)
        writableDatabase.insertWithOnConflict("r", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun range(a: Long, b: Long): List<Rec> {
        val l = mutableListOf<Rec>()
        readableDatabase.rawQuery(
            "SELECT slot,tag,note FROM r WHERE slot>=? AND slot<? ORDER BY slot",
            arrayOf(a.toString(), b.toString())
        ).use { while (it.moveToNext()) l.add(Rec(it.getLong(0), it.getString(1), it.getString(2))) }
        return l
    }

    /** 最常用的标签，用于通知上的快捷按钮 */
    fun top(n: Int): List<String> {
        val l = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT tag,COUNT(*) c FROM r GROUP BY tag ORDER BY c DESC LIMIT $n", null
        ).use { while (it.moveToNext()) l.add(it.getString(0)) }
        return l
    }
}

object P {
    const val DEF = "工作,学习,吃饭,休息,睡觉,通勤,运动,娱乐,家务,社交"
    private fun sp(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)

    fun tags(c: Context): List<String> {
        val l = sp(c).getString("tags", DEF)!!.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return if (l.isEmpty()) DEF.split(",") else l
    }

    fun setTags(c: Context, s: String) = sp(c).edit().putString("tags", s).apply()
    fun qs(c: Context) = sp(c).getInt("qs", 23 * 60)
    fun qe(c: Context) = sp(c).getInt("qe", 7 * 60)
    fun setQ(c: Context, s: Int, e: Int) = sp(c).edit().putInt("qs", s).putInt("qe", e).apply()

    /** 免打扰时段（开始==结束 表示不启用，支持跨午夜） */
    fun inQuiet(c: Context, t: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = t }
        val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val s = qs(c)
        val e = qe(c)
        return when {
            s == e -> false
            s < e -> m in s until e
            else -> m >= s || m < e
        }
    }
}

object Sched {
    const val CH = "log"
    const val NID = 1
    private val F = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    private val FM = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)

    fun next(c: Context, now: Long): Long {
        var t = slotOf(now) + HALF
        var guard = 0
        while (P.inQuiet(c, t) && guard++ < 100) t += HALF
        return t
    }

    fun schedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val t = next(c, System.currentTimeMillis())
        val pi = PendingIntent.getBroadcast(c, 0, Intent(c, AlarmRcv::class.java), F)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        else
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
    }

    fun notifyNow(c: Context, slot: Long) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "记录提醒", NotificationManager.IMPORTANCE_HIGH))
        val b = Notification.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_clock)
            .setContentTitle("现在在做什么？")
            .setContentText("${hhmm(slot)}–${hhmm(slot + HALF)} 这半小时")
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), F))
        val tags = (Db(c).top(2) + P.tags(c)).distinct().take(2)
        tags.forEachIndexed { i, t ->
            val it = Intent(c, ActRcv::class.java).putExtra("slot", slot).putExtra("tag", t)
            b.addAction(Notification.Action.Builder(0, t, PendingIntent.getBroadcast(c, 1 + i, it, F)).build())
        }
        val ri = RemoteInput.Builder("note").setLabel("做了什么…").build()
        val ni = Intent(c, ActRcv::class.java).putExtra("slot", slot)
        b.addAction(
            Notification.Action.Builder(0, "其他…", PendingIntent.getBroadcast(c, 9, ni, FM))
                .addRemoteInput(ri).build()
        )
        nm.notify(NID, b.build())
    }
}

class AlarmRcv : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Sched.schedule(c)
        Sched.notifyNow(c, slotOf(System.currentTimeMillis()))
    }
}

class BootRcv : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Sched.schedule(c)
}

/** 通知上的快捷记录：点标签，或在“其他…”里直接输入 */
class ActRcv : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val slot = i.getLongExtra("slot", 0L)
        val tag = i.getStringExtra("tag")
        val text = RemoteInput.getResultsFromIntent(i)?.getCharSequence("note")?.toString()
        if (slot != 0L) {
            if (tag != null) Db(c).put(slot, tag, "")
            else if (!text.isNullOrBlank()) Db(c).put(slot, "其他", text.trim())
        }
        c.getSystemService(NotificationManager::class.java).cancel(Sched.NID)
    }
}
