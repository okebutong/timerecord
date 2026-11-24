package com.example.timelog

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.*

fun colorOf(tag: String): Int =
    Color.HSVToColor(floatArrayOf(((tag.hashCode() and 0x7fffffff) % 360).toFloat(), 0.55f, 0.85f))

/** 各标签累计时长（横向条形图） */
class Bars(c: Context) : View(c) {
    var data: List<Pair<String, Int>> = emptyList()
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14 * d }

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(MeasureSpec.getSize(w), (data.size * 40 * d).toInt() + 10)

    override fun onDraw(canvas: Canvas) {
        val mx = (data.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
        val lw = 70 * d
        val rw = 80 * d
        data.forEachIndexed { i, (t, n) ->
            val y = i * 40 * d
            p.color = Color.BLACK
            canvas.drawText(t, 4 * d, y + 26 * d, p)
            p.color = colorOf(t)
            val len = (width - lw - rw) * n / mx
            canvas.drawRect(lw, y + 6 * d, lw + len, y + 32 * d, p)
            p.color = Color.DKGRAY
            canvas.drawText("%.1f 小时".format(n / 2.0), lw + len + 6 * d, y + 26 * d, p)
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
        val left = 48 * d
        val cw = (width - left) / 12
        for (i in cells.indices) {
            val row = i / 12
            val col = i % 12
            val x = left + col * cw
            val y = row * 34 * d
            if (col == 0) {
                p.color = Color.DKGRAY
                canvas.drawText("%02d:00".format(row * 6), 0f, y + 20 * d, p)
            }
            p.color = cells[i]?.let { colorOf(it) } ?: Color.LTGRAY
            canvas.drawRect(x + 1, y + 2 * d, x + cw - 1, y + 28 * d, p)
        }
    }
}

class StatsActivity : Activity() {
    private lateinit var bars: Bars
    private lateinit var strip: Strip
    private lateinit var sum: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        title = "统计"
        val pad = (12 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        val row = LinearLayout(this)
        listOf("今天" to 1, "近7天" to 7, "近30天" to 30).forEach { (n, days) ->
            row.addView(Button(this).apply { text = n; setOnClickListener { load(days) } })
        }
        sum = TextView(this)
        strip = Strip(this)
        bars = Bars(this)
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        inner.addView(TextView(this).apply { text = "今天时间轴（每格30分钟，灰色=未记录）" })
        inner.addView(strip)
        inner.addView(TextView(this).apply { text = "各标签累计时长" })
        inner.addView(bars)
        root.addView(row); root.addView(sum)
        root.addView(ScrollView(this).apply { addView(inner) })
        setContentView(root)
        load(1)
    }

    private fun load(days: Int) {
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
