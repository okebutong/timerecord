package com.example.timelog

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** 苹果风“玻璃”样式：柔和渐变背景 + 半透明圆角卡片 + 细白描边 */
object Ui {
    val INK = 0xFF1C1C1E.toInt()
    val INK2 = 0xFF6C6C70.toInt()

    fun dp(c: Context, x: Int) = (x * c.resources.displayMetrics.density).toInt()

    fun alpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a shl 24)

    fun glass(
        c: Context, r: Int,
        fill: Int = 0x73FFFFFF,
        stroke: Int = 0xB3FFFFFF.toInt()
    ): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(c, r).toFloat()
        setStroke(dp(c, 1).coerceAtLeast(1), stroke)
    }

    fun ripple(bg: Drawable): Drawable = RippleDrawable(ColorStateList.valueOf(0x33000000), bg, null)

    /** 窗口背景渐变，状态栏/导航栏透明 */
    fun background(a: Activity) {
        val g = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFFB9D4FF.toInt(), 0xFFD9C8FF.toInt(), 0xFFFFD1E3.toInt())
        )
        a.window.setBackgroundDrawable(g)
        a.window.statusBarColor = Color.TRANSPARENT
        a.window.navigationBarColor = Color.TRANSPARENT
        a.window.isNavigationBarContrastEnforced = false
        @Suppress("DEPRECATION")
        a.window.decorView.systemUiVisibility = a.window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }

    fun card(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = glass(c, 24)
        setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14))
        clipToOutline = true
    }

    fun label(c: Context, s: String, size: Float, color: Int = INK, bold: Boolean = false) =
        TextView(c).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    /** 药丸形按钮 */
    fun chip(c: Context, s: String, fill: Int, onClick: () -> Unit) = TextView(c).apply {
        text = s
        textSize = 14f
        setTextColor(INK)
        gravity = Gravity.CENTER
        setPadding(dp(c, 6), dp(c, 11), dp(c, 6), dp(c, 11))
        background = ripple(glass(c, 16, fill, 0x99FFFFFF.toInt()))
        isClickable = true
        setOnClickListener { onClick() }
    }

    /** 对话框：半透明白 + 背后真实模糊（Android 12+） */
    fun glassDialog(c: Context, d: AlertDialog) {
        val w = d.window ?: return
        w.setBackgroundDrawable(glass(c, 28, 0xE6FFFFFF.toInt()))
        w.setDimAmount(0.15f)
        if (Build.VERSION.SDK_INT >= 31) {
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            val lp = w.attributes
            lp.blurBehindRadius = 40
            w.attributes = lp
        }
    }
}
