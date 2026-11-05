package com.whalemate.wechat

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs

/** 悬浮球：点球 = 分析；点右上角 × = 关闭悬浮球。可拖动。 */
object FloatingBall {
    private var view: View? = null
    private var manager: WindowManager? = null

    fun show(ctx: Context, onTap: () -> Unit, onClose: () -> Unit) {
        if (view?.isAttachedToWindow == true) return
        hide()

        val app = ctx.applicationContext
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = app.resources.displayMetrics.density
        val size = (60 * density).toInt()
        val closeSize = (24 * density).toInt()

        val ball = TextView(app).apply {
            text = "鲸"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            isClickable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#2F6BFF"))
            }
            elevation = 6 * density
        }

        val close = TextView(app).apply {
            text = "×"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#888888"))
            }
            setOnClickListener { onClose() }
        }

        val container = FrameLayout(app).apply {
            addView(ball, FrameLayout.LayoutParams(size, size))
            addView(close, FrameLayout.LayoutParams(closeSize, closeSize).apply {
                gravity = Gravity.TOP or Gravity.END
            })
        }

        val windowSize = size + closeSize / 2
        val params = WindowManager.LayoutParams(
            windowSize,
            windowSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = app.resources.displayMetrics.widthPixels - windowSize - (12 * density).toInt()
            y = (app.resources.displayMetrics.heightPixels * 0.35f).toInt()
        }

        val slop = ViewConfiguration.get(app).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = params.x
        var startY = params.y
        var moved = false

        val detector = GestureDetector(app, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                onTap()
                return true
            }
        })

        ball.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                    if (moved) {
                        params.x = startX + dx
                        params.y = startY + dy
                        try {
                            wm.updateViewLayout(container, params)
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
            true
        }

        try {
            wm.addView(container, params)
            view = container
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
