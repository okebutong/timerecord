package com.example.timelog

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*

fun colorOf(tag: String): Int =
    Color.HSVToColor(floatArrayOf(((tag.hashCode() and 0x7fffffff) % 360).toFloat(), 0.5f, 0.92f))

/** 各标签累计时长（圆角横向条形图） */
class Bars(c: Context) : View(c) {
    var data: List<Pair<String, Int>> = emptyList()
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14 * d }

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(MeasureSpec.getSize(w), (data.size * 40 * d).toInt() + 10)

    override fun onDraw(canvas: Canvas) {
        val mx = (data.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
        val lw = 64 * d
        val rw = 76 * d
        data.forEachIndexed { i, (t, n) ->
            val y = i * 40 * d
            p.color = Ui.INK
            canvas.drawText(t, 2 * d, y + 25 * d, p)
            p.color = colorOf(t)
            val len = ((width - lw - rw) * n / mx).coerceAtLeast(8 * d)
            canvas.drawRoundRect(RectF(lw, y + 7 * d, lw + len, y + 31 * d), 8 * d, 8 * d, p)
            p.color = Ui.INK2
            canvas.drawText("%.1f 小时".format(n / 2.0), lw + len + 8 * d, y + 25 * d, p)
        }
    }
}

/** 今天的 48 格时间轴，每行 6 小时 */
class Strip(c: Context) : View(c) {
    var cells: List<String?> = emptyList()
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11 * d }

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(MeasureSpec.getSize(w), (4 * 34 * d).toInt() + 10)

    override fun onDraw(canvas: Canvas) {
        val left = 46 * d
        val cw = (width - left) / 12
        for (i in cells.indices) {
            val row = i / 12
            val col = i % 12
            val x = left + col * cw
            val y = row * 34 * d
            if (col == 0) {
                p.color = Ui.INK2
                canvas.drawText("%02d:00".format(row * 6), 0f, y + 20 * d, p)
            }
            p.color = cells[i]?.let { colorOf(it) } ?: 0x1F000000
            canvas.drawRoundRect(RectF(x + 1.5f * d, y + 3 * d, x + cw - 1.5f * d, y + 29 * d), 5 * d, 5 * d, p)
        }
    }
}

class StatsActivity : Activity() {
    private lateinit var bars: Bars
    private lateinit var strip: Strip
    private lateinit var sum: TextView
    private lateinit var segs: List<TextView>
    private val opts = listOf("今天" to 1, "近7天" to 7, "近30天" to 30)

    private fun dp(x: Int) = Ui.dp(this, x)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Ui.background(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        root.addView(Ui.label(this, "统计", 30f, bold = true))

        // 分段选择器
        val seg = LinearLayout(this).apply {
            background = Ui.glass(this@StatsActivity, 14, 0x55FFFFFF, 0x66FFFFFF)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        segs = opts.map { (n, days) ->
            Ui.label(this, n, 14f).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
                setOnClickListener { load(days) }
            }
        }
        segs.forEach { seg.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        root.addView(seg, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        sum = Ui.label(this, "", 14f, Ui.INK2)
        root.addView(sum, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); leftMargin = dp(4) })

        strip = Strip(this)
        bars = Bars(this)
        val c1 = Ui.card(this)
        c1.addView(Ui.label(this, "今天时间轴", 16f, bold = true))
        c1.addView(Ui.label(this, "每格 30 分钟，灰色 = 未记录", 12f, Ui.INK2))
        c1.addView(strip, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val c2 = Ui.card(this)
        c2.addView(Ui.label(this, "各标签累计时长", 16f, bold = true))
        c2.addView(bars, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        inner.addView(c1)
        inner.addView(c2, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        root.addView(ScrollView(this).apply { addView(inner) },
            LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(4) })
        setContentView(root)
        load(1)
    }

    private fun load(days: Int) {
        segs.forEachIndexed { i, tv ->
            tv.background = if (opts[i].second == days)
                Ui.glass(this, 11, 0xE6FFFFFF.toInt(), 0x00FFFFFF) else null
        }
        val db = Db(this)
        val rs = db.range(dayStart(1 - days), dayStart(1))
        bars.data = rs.groupingBy { it.tag }.eachCount().toList().sortedByDescending { it.second }
        bars.requestLayout(); bars.invalidate()
        val t0 = dayStart(0)
        val today = db.range(t0, dayStart(1)).associateBy { it.slot }
        strip.cells = (0 until 48).map { today[t0 + it * HALF]?.tag }
        strip.invalidate()
        sum.text = "已记录 ${rs.size} 格（${rs.size / 2.0} 小时），完成度 ${rs.size * 100 / (days * 48)}%"
    }
}
