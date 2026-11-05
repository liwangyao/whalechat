package com.whalemate.wechat

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var settings: AppSettings
    private lateinit var keyEdit: EditText
    private lateinit var urlEdit: EditText
    private lateinit var modelEdit: EditText
    private lateinit var contactEdit: EditText
    private lateinit var notesEdit: EditText
    private lateinit var dshUrlEdit: EditText
    private lateinit var dshTokenEdit: EditText
    private lateinit var backendGroup: RadioGroup
    private lateinit var modeGroup: RadioGroup
    private lateinit var stageSpinner: Spinner
    private lateinit var autoSwitch: Switch
    private lateinit var ballSwitch: Switch
    private lateinit var autoAnalyzeSwitch: Switch
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(48))
        }
        setContentView(ScrollView(this).apply { addView(content) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        }

        content.addView(title("鲸聊 · 微信 AI 回复"))
        content.addView(hint("读取微信聊天，生成候选回复；点一条发送。"))

        content.addView(hint("回复来源"))
        backendGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        for (pair in listOf("direct" to "直连 DeepSeek（自己填 Key）", "dsh" to "接入我（DSH 桥服务）")) {
            backendGroup.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = pair.second
                tag = pair.first
                isChecked = settings.backend == pair.first
            })
        }
        content.addView(backendGroup)

        content.addView(hint("DeepSeek API Key（直连时用）"))
        keyEdit = input(settings.apiKey, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        content.addView(keyEdit)

        content.addView(hint("Base URL"))
        urlEdit = input(settings.baseUrl, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        content.addView(urlEdit)

        content.addView(hint("模型"))
        modelEdit = input(settings.model, InputType.TYPE_CLASS_TEXT)
        content.addView(modelEdit)

        content.addView(hint("桥服务地址（DSH 模式）"))
        dshUrlEdit = input(settings.dshUrl, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        content.addView(dshUrlEdit)

        content.addView(hint("桥服务 Token"))
        dshTokenEdit = input(settings.dshToken, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        content.addView(dshTokenEdit)

        content.addView(hint("回复模式"))
        modeGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val modes = listOf(
            "jev" to "Jev：一条自然的短句",
            "goutou" to "狗头军师：分析 + 1~3 条",
            "love" to "恋爱大师：3 条 + 预测"
        )
        for (mode in modes) {
            modeGroup.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = mode.second
                tag = mode.first
                isChecked = settings.mode == mode.first
            })
        }
        content.addView(modeGroup)

        content.addView(hint("对方阶段"))
        stageSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("刚加上", "聊过几次", "暧昧")
            )
            setSelection((settings.stage - 1).coerceIn(0, 2))
        }
        content.addView(stageSpinner)

        content.addView(hint("对方昵称（可空）"))
        contactEdit = input(settings.contactName, InputType.TYPE_CLASS_TEXT)
        content.addView(contactEdit)

        content.addView(hint("备注（可空，比如她的喜好）"))
        notesEdit = input(settings.notes, InputType.TYPE_CLASS_TEXT)
        content.addView(notesEdit)

        autoSwitch = Switch(this).apply {
            text = "自动发送（默认关，建议先手动点）"
            isChecked = settings.autoSend
        }
        content.addView(autoSwitch)

        ballSwitch = Switch(this).apply {
            text = "常驻悬浮球（点一下分析当前屏幕）"
            isChecked = settings.showBall
        }
        content.addView(ballSwitch)

        autoAnalyzeSwitch = Switch(this).apply {
            text = "新消息自动分析（默认关）"
            isChecked = settings.autoAnalyze
        }
        content.addView(autoAnalyzeSwitch)

        content.addView(button("保存设置") { save() })
        content.addView(button("① 打开无障碍设置") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        content.addView(button("② 授予悬浮窗权限") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        })
        content.addView(button("③ 复制当前屏幕结构（调试）") { dumpScreen() })

        statusView = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(16), 0, 0)
            setTextColor(0xFF555555.toInt())
        }
        content.addView(statusView)

        content.addView(hint("用法：消息来了 → 下拉快捷开关点「鲸聊」→ 打开微信聊天 → 点悬浮球 → 选一条发。不用时常按关掉。"))
    }

    override fun onResume() {
        super.onResume()
        if (::statusView.isInitialized) {
            val a11y = if (ChatA11yService.instance != null) "无障碍：已连接" else "无障碍：未连接"
            val overlay = if (Settings.canDrawOverlays(this)) "悬浮窗：已授权" else "悬浮窗：未授权（用通知栏）"
            val ball = if (settings.showBall) "悬浮球：开" else "悬浮球：关"
            val backend = if (settings.backend == "dsh") "来源：DSH 桥" else "来源：直连 DeepSeek"
            val key = if (settings.apiKey.isBlank()) "Key：未填" else "Key：已填"
            statusView.text = "$a11y\n$overlay\n$ball\n$backend\n$key"
        }
        if (settings.showBall && Settings.canDrawOverlays(this)) BallService.start(this)
    }

    private fun save() {
        settings.apiKey = keyEdit.text.toString().trim()
        settings.baseUrl = urlEdit.text.toString().trim().ifBlank { "https://api.deepseek.com" }
        settings.model = modelEdit.text.toString().trim().ifBlank { "deepseek-chat" }
        settings.dshUrl = dshUrlEdit.text.toString().trim().ifBlank { "http://127.0.0.1:3220" }
        settings.dshToken = dshTokenEdit.text.toString().trim()
        settings.contactName = contactEdit.text.toString().trim()
        settings.notes = notesEdit.text.toString().trim()
        settings.autoSend = autoSwitch.isChecked
        settings.showBall = ballSwitch.isChecked
        settings.autoAnalyze = autoAnalyzeSwitch.isChecked
        settings.stage = stageSpinner.selectedItemPosition + 1

        val backend = findViewById<RadioButton>(backendGroup.checkedRadioButtonId)
        settings.backend = (backend?.tag as? String) ?: "direct"

        val mode = findViewById<RadioButton>(modeGroup.checkedRadioButtonId)
        settings.mode = (mode?.tag as? String) ?: "jev"

        if (settings.showBall) BallService.start(this) else BallService.stop(this)

        toast("已保存")
    }

    private fun dumpScreen() {
        val service = ChatA11yService.instance
        if (service == null) {
            toast("无障碍服务没开")
            return
        }
        val dump = service.dumpScreen()
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("whalechat-dump", dump))
        toast("已复制 ${dump.length} 字")
    }

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        textSize = 21f
        setPadding(0, 0, 0, dp(6))
    }

    private fun hint(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(0xFF666666.toInt())
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun input(value: String, type: Int) = EditText(this).apply {
        setText(value)
        inputType = type
        textSize = 15f
        setSingleLine(true)
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
