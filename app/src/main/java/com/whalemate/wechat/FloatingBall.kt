package com.whalemate.wechat

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/** 常驻悬浮球：点一下 = 读当前屏幕；可拖动。 */
object FloatingBall {
    private var view: View? = null
    private var manager: WindowManager? = null

    fun show(ctx: Context, onClick: () -> Unit) {
        if (view?.isAttachedToWindow == true) return
        hide()

        val app = ctx.applicationContext
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = app.resources.displayMetrics.density
        val size = (56 * density).toInt()

        val ball = TextView(app).apply {
            text = "鲸"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2F6BFF"))
            }
            elevation = 6 * density
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = app.resources.displayMetrics.widthPixels - size - (10 * density).toInt()
            y = (app.resources.displayMetrics.heightPixels * 0.35f).toInt()
        }

        var downX = 0f
        var downY = 0f
        var startX = params.x
        var startY = params.y
        var moved = false

        ball.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    try {
                        wm.updateViewLayout(v, params)
                    } catch (_: Throwable) {
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) onClick()
                    true
                }

                else -> false
            }
        }

        try {
            wm.addView(ball, params)
            view = ball
            manager = wm
        } catch (t: Throwable) {
            android.widget.Toast.makeText(app, "悬浮球添加失败：${t.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun hide() {
        val v = view ?: return
        try {
            manager?.removeView(v)
        } catch (_: Throwable) {
        }
        view = null
        manager = null
    }
}
