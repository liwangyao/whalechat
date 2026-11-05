package com.whalemate.wechat

import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** 无键盘输入法：只负责把候选回复 commit 进当前输入框。 */
class WhaleIme : InputMethodService() {

    companion object {
        @Volatile
        var instance: WhaleIme? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 10, 24, 10)
            setBackgroundColor(0xFFF5F5F5.toInt())
        }
        bar.addView(TextView(this).apply {
            text = "鲸聊输入法（无键盘，只用来填入候选）"
            textSize = 13f
            setTextColor(0xFF666666.toInt())
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(Button(this).apply {
            text = "切换键盘"
            textSize = 13f
            isAllCaps = false
            setOnClickListener { switchToNextInputMethod(false) }
        })
        return bar
    }

    fun commit(text: String): Boolean {
        val connection = currentInputConnection ?: return false
        connection.commitText(text, 1)
        return true
    }
}
