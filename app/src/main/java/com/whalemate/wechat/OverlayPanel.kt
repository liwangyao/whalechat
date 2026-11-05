package com.whalemate.wechat

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object OverlayPanel {
    private var panel: View? = null
    private var manager: WindowManager? = null

    fun show(
        ctx: Context,
        result: BrainResult,
        modeLabel: String,
        onPick: (String) -> Unit,
        onRetry: () -> Unit
    ) {
        dismiss()

        val app = ctx.applicationContext
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val root = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(app, 14), dp(app, 12), dp(app, 14), dp(app, 10))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(app, 16).toFloat()
                setStroke(dp(app, 1), Color.parseColor("#E0E0E0"))
            }
            elevation = dp(app, 10).toFloat()
        }

        root.addView(TextView(app).apply {
            text = "鲸聊 · $modeLabel"
            textSize = 15f
            setTextColor(Color.parseColor("#222222"))
        })

        if (result.analysis.isNotBlank()) {
            root.addView(TextView(app).apply {
                text = result.analysis
                textSize = 13f
                setTextColor(Color.parseColor("#666666"))
                maxLines = 4
                setPadding(0, dp(app, 6), 0, dp(app, 2))
            })
        }

        for (option in result.options.take(3)) {
            root.addView(Button(app).apply {
                text = if (option.tone.isBlank()) option.text else "[${option.tone}] ${option.text}"
                textSize = 15f
                isAllCaps = false
                setOnClickListener {
                    dismiss()
                    onPick(option.text)
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(app, 6) })
        }

        val actions = LinearLayout(app).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(app, 8), 0, 0)
        }
        actions.addView(TextView(app).apply {
            text = "重读"
            textSize = 13f
            setTextColor(Color.parseColor("#2F6BFF"))
            setPadding(dp(app, 12), dp(app, 4), dp(app, 12), dp(app, 4))
            setOnClickListener {
                dismiss()
                onRetry()
            }
        })
        actions.addView(TextView(app).apply {
            text = "关闭"
            textSize = 13f
            setTextColor(Color.parseColor("#999999"))
            setPadding(dp(app, 12), dp(app, 4), dp(app, 4), dp(app, 4))
            setOnClickListener { dismiss() }
        })
        root.addView(actions)

        val params = WindowManager.LayoutParams(
            (app.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = dp(app, 140)
        }

        try {
            wm.addView(root, params)
            panel = root
            manager = wm
        } catch (t: Throwable) {
            Toast.makeText(app, "没有悬浮窗权限", Toast.LENGTH_SHORT).show()
        }
    }

    fun dismiss() {
        val v = panel ?: return
        try {
            manager?.removeView(v)
        } catch (_: Throwable) {
        }
        panel = null
        manager = null
    }

    private fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density).toInt()
}
