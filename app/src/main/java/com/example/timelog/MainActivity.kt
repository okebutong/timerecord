package com.example.timelog

import android.app.*
import android.content.*
import android.graphics.Typeface
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
    private lateinit var adapter: SlotAdapter
    private var slots = listOf<Long>()
    private var recs = mapOf<Long, Rec>()

    private fun dp(x: Int) = Ui.dp(this, x)
    private val white = 0x66FFFFFF

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Ui.background(this)
        db = Db(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        Sched.schedule(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        // 大标题 + 日期切换
        root.addView(Ui.label(this, "半小时记录", 30f, bold = true))
        val nav = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.glass(this@MainActivity, 18, 0x55FFFFFF)
        }
        val prev = Ui.label(this, "‹", 24f).apply {
            gravity = Gravity.CENTER; setPadding(dp(18), dp(2), dp(18), dp(4))
            setOnClickListener { day--; target = dayStart(day); refresh() }
        }
        val next = Ui.label(this, "›", 24f).apply {
            gravity = Gravity.CENTER; setPadding(dp(18), dp(2), dp(18), dp(4))
            setOnClickListener {
                if (day < 0) {
                    day++
                    target = if (day == 0) slotOf(System.currentTimeMillis()) else dayStart(day)
                    refresh()
                }
            }
        }
        dateTv = Ui.label(this, "", 15f, Ui.INK2).apply { gravity = Gravity.CENTER }
        nav.addView(prev)
        nav.addView(dateTv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(next)
        root.addView(nav, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        // 记录卡片
        val rec = Ui.card(this)
        head = Ui.label(this, "", 17f, bold = true)
        note = EditText(this).apply {
            hint = "备注（可选），点下面标签即保存"
            textSize = 15f
            setSingleLine()
            setTextColor(Ui.INK)
            setHintTextColor(Ui.INK2)
            background = Ui.glass(this@MainActivity, 14, 0x55FFFFFF, 0x66FFFFFF)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        tagBox = GridLayout(this).apply { columnCount = 4 }
        rec.addView(head)
        rec.addView(note, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(6) })
        rec.addView(tagBox)
        root.addView(rec, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        // 操作按钮
        val acts = LinearLayout(this)
        fun act(t: String, f: () -> Unit) = acts.addView(
            Ui.chip(this, t, white, f),
            LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) }
        )
        act("统计") { startActivity(Intent(this, StatsActivity::class.java)) }
        act("导出 CSV") { export() }
        act("设置") { settings() }
        root.addView(acts, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        // 当天 48 个时段
        adapter = SlotAdapter()
        list = ListView(this).apply {
            divider = null
            selector = android.graphics.drawable.ColorDrawable(0)
            this.adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, pos, _ ->
                target = slots[pos]
                note.setText(recs[target]?.note ?: "")
                refresh()
            }
        }
        val listCard = Ui.card(this).apply { setPadding(dp(6), dp(6), dp(6), dp(6)) }
        listCard.addView(list, LinearLayout.LayoutParams(-1, -1))
        root.addView(listCard, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })

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
            val chip = Ui.chip(this, t, Ui.alpha(colorOf(t), 0x80)) { save(t) }
            chip.layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply { width = 0; setMargins(dp(3), dp(3), dp(3), dp(3)) }
            tagBox.addView(chip)
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
        dateTv.text = SimpleDateFormat("M月d日 EEEE", Locale.getDefault()).format(Date(from)) +
                if (day == 0) " · 今天" else ""
        head.text = "${hhmm(target)} – ${hhmm(target + HALF)}" +
                (recs[target]?.let { "　已记：${it.tag}" } ?: "　现在在做什么？")
        adapter.notifyDataSetChanged()
    }

    private inner class SlotAdapter : BaseAdapter() {
        override fun getCount() = slots.size
        override fun getItem(p: Int) = slots[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup): View {
            val s = slots[p]
            val r = recs[s]
            val row = LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(9), dp(10), dp(9))
                if (s == target) background = Ui.glass(this@MainActivity, 14, 0x99FFFFFF, 0xCCFFFFFF.toInt())
            }
            row.addView(Ui.label(this@MainActivity, hhmm(s), 14f, Ui.INK2).apply {
                typeface = Typeface.MONOSPACE
                minWidth = dp(54)
            })
            if (r == null) {
                row.addView(Ui.label(this@MainActivity, "—", 14f, Ui.alpha(Ui.INK2, 0x80)))
            } else {
                row.addView(Ui.label(this@MainActivity, r.tag, 13f).apply {
                    setPadding(dp(10), dp(3), dp(10), dp(3))
                    background = Ui.glass(this@MainActivity, 10, Ui.alpha(colorOf(r.tag), 0x99), 0x66FFFFFF)
                })
                if (r.note.isNotEmpty()) row.addView(
                    Ui.label(this@MainActivity, r.note, 13f, Ui.INK2).apply {
                        setSingleLine(); ellipsize = TextUtils.TruncateAt.END
                    },
                    LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) }
                )
            }
            return row
        }
    }

    private fun hm(x: Int) = "%02d:%02d".format(x / 60, x % 60)

    private fun settings() {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        fun lp() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        val et = EditText(this).apply {
            setText(P.tags(this@MainActivity).joinToString(","))
            hint = "标签，用逗号分隔"
            textSize = 15f
            setTextColor(Ui.INK)
            background = Ui.glass(this@MainActivity, 14, 0x55FFFFFF, 0x66FFFFFF)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val bs = Ui.chip(this, "", white) {}
        val be = Ui.chip(this, "", white) {}
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
        v.addView(Ui.label(this, "标签（逗号分隔）", 13f, Ui.INK2))
        v.addView(et, lp())
        v.addView(bs, lp()); v.addView(be, lp())
        v.addView(Ui.label(this, "开始和结束设成相同时间 = 不启用免打扰", 12f, Ui.INK2), lp())
        v.addView(Ui.chip(this, "允许精确闹钟（让提醒准点）", white) {
            if (Build.VERSION.SDK_INT >= 31)
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }, lp())
        v.addView(Ui.chip(this, "电池优化设置（建议设为不受限制）", white) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }, lp())
        val d = AlertDialog.Builder(this).setTitle("设置").setView(v)
            .setPositiveButton("保存") { _, _ ->
                if (et.text.isNotBlank()) P.setTags(this, et.text.toString())
                Sched.schedule(this)
                buildTags()
            }.setNegativeButton("取消", null).create()
        Ui.glassDialog(this, d)
        d.show()
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
