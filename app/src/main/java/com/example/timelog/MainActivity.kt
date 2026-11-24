package com.example.timelog

import android.app.*
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var db: Db
    private var day = 0
    private var target = 0L
    private lateinit var head: TextView
    private lateinit var dateTv: TextView
    private lateinit var note: EditText
    private lateinit var tagBox: GridLayout
    private lateinit var list: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private var slots = listOf<Long>()
    private var recs = mapOf<Long, Rec>()

    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setOnClickListener { f() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = Db(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        Sched.schedule(this)

        val pad = (12 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        head = TextView(this).apply { textSize = 18f }
        note = EditText(this).apply { hint = "备注（可选），点下面的标签即保存"; setSingleLine() }
        tagBox = GridLayout(this).apply { columnCount = 4 }
        dateTv = TextView(this).apply { textSize = 16f; gravity = android.view.Gravity.CENTER }
        val nav = LinearLayout(this)
        nav.addView(btn("◀") { day--; target = dayStart(day); refresh() })
        nav.addView(dateTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(btn("▶") {
            if (day < 0) { day++; target = if (day == 0) slotOf(System.currentTimeMillis()) else dayStart(day); refresh() }
        })
        val acts = LinearLayout(this)
        acts.addView(btn("统计") { startActivity(Intent(this, StatsActivity::class.java)) })
        acts.addView(btn("导出CSV") { export() })
        acts.addView(btn("设置") { settings() })
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        list = ListView(this).apply { this.adapter = this@MainActivity.adapter }
        list.setOnItemClickListener { _, _, pos, _ ->
            target = slots[pos]
            note.setText(recs[target]?.note ?: "")
            refresh()
        }
        root.addView(head); root.addView(note); root.addView(tagBox); root.addView(nav); root.addView(acts)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        day = 0
        target = slotOf(System.currentTimeMillis())
        buildTags()
        refresh()
        list.setSelection((slots.indexOf(target) - 3).coerceAtLeast(0))
    }

    private fun buildTags() {
        tagBox.removeAllViews()
        P.tags(this).forEach { t ->
            val b = btn(t) { save(t) }
            b.layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply { width = 0 }
            tagBox.addView(b)
        }
    }

    private fun save(t: String) {
        db.put(target, t, note.text.toString().trim())
        note.setText("")
        if (target == slotOf(System.currentTimeMillis()))
            getSystemService(NotificationManager::class.java).cancel(Sched.NID)
        Toast.makeText(this, "已记录 ${hhmm(target)} $t", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun refresh() {
        val from = dayStart(day)
        val to = dayStart(day + 1)
        slots = generateSequence(from) { it + HALF }.takeWhile { it < to }.toList()
        recs = db.range(from, to).associateBy { it.slot }
        dateTv.text = SimpleDateFormat("yyyy-MM-dd EEE", Locale.getDefault()).format(Date(from)) +
                if (day == 0) "（今天）" else ""
        head.text = "正在记录 ${hhmm(target)}–${hhmm(target + HALF)}" +
                (recs[target]?.let { "（已记：${it.tag}）" } ?: "")
        adapter.clear()
        adapter.addAll(slots.map { sl ->
            hhmm(sl) + "   " + (recs[sl]?.let { it.tag + if (it.note.isNotEmpty()) " · " + it.note else "" } ?: "—")
        })
    }

    private fun hm(x: Int) = "%02d:%02d".format(x / 60, x % 60)

    private fun settings() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad / 2, pad, 0) }
        val et = EditText(this).apply { setText(P.tags(this@MainActivity).joinToString(",")); hint = "标签，用逗号分隔" }
        val bs = Button(this)
        val be = Button(this)
        fun upd() {
            bs.text = "免打扰开始  " + hm(P.qs(this))
            be.text = "免打扰结束  " + hm(P.qe(this))
        }
        bs.setOnClickListener {
            TimePickerDialog(this, { _, h, m -> P.setQ(this, h * 60 + m, P.qe(this)); upd() },
                P.qs(this) / 60, P.qs(this) % 60, true).show()
        }
        be.setOnClickListener {
            TimePickerDialog(this, { _, h, m -> P.setQ(this, P.qs(this), h * 60 + m); upd() },
                P.qe(this) / 60, P.qe(this) % 60, true).show()
        }
        upd()
        v.addView(TextView(this).apply { text = "标签（逗号分隔）" })
        v.addView(et)
        v.addView(bs); v.addView(be)
        v.addView(TextView(this).apply { text = "开始和结束设成相同时间 = 不启用免打扰" })
        v.addView(btn("允许精确闹钟（让提醒准点）") {
            if (Build.VERSION.SDK_INT >= 31)
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        })
        v.addView(btn("电池优化设置（建议设为不受限制）") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        AlertDialog.Builder(this).setTitle("设置").setView(v)
            .setPositiveButton("保存") { _, _ ->
                if (et.text.isNotBlank()) P.setTags(this, et.text.toString())
                Sched.schedule(this)
                buildTags()
            }.setNegativeButton("取消", null).show()
    }

    private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    private fun export() {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder("\uFEFF开始时间,标签,备注\n")
        db.range(0, Long.MAX_VALUE).forEach {
            sb.append(f.format(Date(it.slot))).append(',').append(csv(it.tag)).append(',').append(csv(it.note)).append('\n')
        }
        val name = "timelog_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".csv"
        val cv = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        try {
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
            contentResolver.openOutputStream(uri)!!.use { it.write(sb.toString().toByteArray()) }
            Toast.makeText(this, "已导出到“下载”文件夹：$name", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
