package com.whalemate.wechat

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
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

    private val bg = Color.parseColor("#F3F4F6")
    private val cardColor = Color.WHITE
    private val textColor = Color.parseColor("#111827")
    private val muted = Color.parseColor("#6B7280")
    private val primary = Color.parseColor("#2F6BFF")
    private val primarySoft = Color.parseColor("#E9F0FF")
    private val okBg = Color.parseColor("#DCFCE7")
    private val okText = Color.parseColor("#166534")
    private val badBg = Color.parseColor("#FEE2E2")
    private val badText = Color.parseColor("#991B1B")

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
    private lateinit var customNameEdit: EditText
    private lateinit var customPromptEdit: EditText
    private lateinit var statusView: TextView
    private lateinit var chipA11y: TextView
    private lateinit var chipOverlay: TextView
    private lateinit var chipBall: TextView
    private lateinit var chipIme: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)

        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (Build.VERSION.SDK_INT >= 23) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(20), dp(26), dp(20), dp(40))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(root)
        })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        }

        root.addView(TextView(this).apply {
            text = "鲸聊"
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
        })
        root.addView(TextView(this).apply {
            text = "微信 AI 回复助手"
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(4), 0, dp(18))
        })

        val statusCard = card()
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chipA11y = chip("无障碍")
        chipOverlay = chip("悬浮窗")
        chipBall = chip("悬浮球")
        chipIme = chip("输入法")
        for (c in listOf(chipA11y, chipOverlay, chipBall, chipIme)) {
            chips.addView(c, LinearLayout.LayoutParams(0, dp(30), 1f).apply { marginEnd = dp(6) })
        }
        statusCard.addView(chips)
        statusView = TextView(this).apply {
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(10), 0, 0)
        }
        statusCard.addView(statusView)
        root.addView(statusCard)

        root.addView(sectionTitle("回复来源"))
        val sourceCard = card()
        backendGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        addRadio(backendGroup, "direct", "直连 DeepSeek（自己填 Key）", settings.backend == "direct")
        addRadio(backendGroup, "dsh", "接入我（DSH 桥服务）", settings.backend == "dsh")
        sourceCard.addView(backendGroup)

        sourceCard.addView(label("DeepSeek API Key"))
        keyEdit = input(settings.apiKey, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        sourceCard.addView(keyEdit)

        sourceCard.addView(label("Base URL"))
        urlEdit = input(settings.baseUrl, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        sourceCard.addView(urlEdit)

        sourceCard.addView(label("模型"))
        modelEdit = input(settings.model, InputType.TYPE_CLASS_TEXT)
        sourceCard.addView(modelEdit)

        sourceCard.addView(label("桥服务地址"))
        dshUrlEdit = input(settings.dshUrl, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        sourceCard.addView(dshUrlEdit)

        sourceCard.addView(label("桥服务 Token"))
        dshTokenEdit = input(settings.dshToken, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        sourceCard.addView(dshTokenEdit)
        root.addView(sourceCard)

        root.addView(sectionTitle("插件"))
        val modeCard = card()
        modeGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        addRadio(modeGroup, "jev", "Jev：一条自然的短句", settings.mode == "jev")
        addRadio(modeGroup, "goutou", "狗头军师：分析 + 1~3 条", settings.mode == "goutou")
        addRadio(modeGroup, "love", "恋爱大师：3 条 + 预测", settings.mode == "love")
        addRadio(modeGroup, "eq", "高情商：先接情绪再回", settings.mode == "eq")
        addRadio(modeGroup, "fun", "幽默：轻松接梗", settings.mode == "fun")
        addRadio(modeGroup, "direct", "直球：说清楚", settings.mode == "direct")
        addRadio(modeGroup, "repair", "修复：道歉 / 和好", settings.mode == "repair")
        addRadio(modeGroup, "tease", "会撩：松弛感 / 轻推拉", settings.mode == "tease")
        addRadio(modeGroup, "prank", "整活：职场段子 / 阴阳怪气", settings.mode == "prank")
        addRadio(modeGroup, "custom", "自定义：用下面填的提示词", settings.mode == "custom")
        modeCard.addView(modeGroup)

        modeCard.addView(label("对方阶段"))
        stageSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("刚加上", "聊过几次", "暧昧")
            )
            setSelection((settings.stage - 1).coerceIn(0, 2))
        }
        modeCard.addView(stageSpinner)

        modeCard.addView(label("对方昵称"))
        contactEdit = input(settings.contactName, InputType.TYPE_CLASS_TEXT)
        modeCard.addView(contactEdit)

        modeCard.addView(label("备注（她的喜好、雷点等）"))
        notesEdit = input(settings.notes, InputType.TYPE_CLASS_TEXT)
        modeCard.addView(notesEdit)

        modeCard.addView(label("自定义插件名"))
        customNameEdit = input(settings.customName, InputType.TYPE_CLASS_TEXT)
        modeCard.addView(customNameEdit)

        modeCard.addView(label("自定义提示词（可粘贴 GitHub 上的）"))
        customPromptEdit = EditText(this).apply {
            setText(settings.customPrompt)
            textSize = 14f
            minLines = 4
            gravity = Gravity.TOP
            setTextColor(textColor)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.parseColor("#F3F4F6"), 12)
        }
        modeCard.addView(customPromptEdit)
        root.addView(modeCard)

        root.addView(sectionTitle("开关"))
        val switchCard = card()
        autoSwitch = styledSwitch("自动发送（默认关）", settings.autoSend)
        ballSwitch = styledSwitch("常驻悬浮球", settings.showBall)
        autoAnalyzeSwitch = styledSwitch("新消息自动分析", settings.autoAnalyze)
        switchCard.addView(autoSwitch)
        switchCard.addView(ballSwitch)
        switchCard.addView(autoAnalyzeSwitch)
        root.addView(switchCard)

        root.addView(sectionTitle("权限与工具"))
        val toolsCard = card()
        toolsCard.addView(primaryButton("保存设置") { save() })
        toolsCard.addView(secondaryButton("① 打开无障碍设置") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        toolsCard.addView(secondaryButton("② 授予悬浮窗权限") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        })
        toolsCard.addView(secondaryButton("③ 启用鲸聊输入法") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        toolsCard.addView(secondaryButton("④ 切换到鲸聊输入法") {
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        })
        toolsCard.addView(secondaryButton("⑤ 复制当前屏幕结构（调试）") { dumpScreen() })
        root.addView(toolsCard)

        root.addView(TextView(this).apply {
            text = "用法：消息来了 → 下拉快捷开关点「鲸聊」→ 打开微信聊天 → 点球选插件 → 选一条发送。"
            textSize = 12f
            setTextColor(muted)
            setPadding(dp(4), dp(8), dp(4), 0)
            setLineSpacing(dp(4).toFloat(), 1f)
        })
    }

    override fun onResume() {
        super.onResume()
        if (::statusView.isInitialized) {
            val a11yOn = ChatA11yService.instance != null
            val overlayOn = Settings.canDrawOverlays(this)
            val imeId = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
            val imeOn = imeId.contains(packageName)

            setChip(chipA11y, a11yOn)
            setChip(chipOverlay, overlayOn)
            setChip(chipBall, settings.showBall)
            setChip(chipIme, imeOn)

            val backend = if (settings.backend == "dsh") "DSH 桥" else "直连 DeepSeek"
            val key = if (settings.apiKey.isBlank()) "Key 未填" else "Key 已填"
            statusView.text = "来源：$backend · $key"
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
        settings.customName = customNameEdit.text.toString().trim().ifBlank { "自定义" }
        settings.customPrompt = customPromptEdit.text.toString().trim()
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

    private fun addRadio(group: RadioGroup, tagValue: String, label: String, checked: Boolean) {
        group.addView(RadioButton(this).apply {
            id = View.generateViewId()
            text = label
            this.tag = tagValue
            isChecked = checked
            textSize = 14f
            setTextColor(textColor)
            setPadding(0, dp(4), 0, dp(4))
        })
    }

    private fun styledSwitch(label: String, checked: Boolean) = Switch(this).apply {
        text = label
        textSize = 14f
        setTextColor(textColor)
        isChecked = checked
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(16))
        background = rounded(cardColor, 18)
        elevation = dp(2).toFloat()
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        setTextColor(muted)
        setPadding(dp(4), dp(18), 0, dp(8))
    }

    private fun chip(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(muted)
        background = rounded(Color.parseColor("#E5E7EB"), 10)
    }

    private fun setChip(chip: TextView, on: Boolean) {
        chip.background = rounded(if (on) okBg else badBg, 10)
        chip.setTextColor(if (on) okText else badText)
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(muted)
        setPadding(dp(2), dp(10), 0, dp(4))
    }

    private fun input(value: String, type: Int) = EditText(this).apply {
        setText(value)
        inputType = type
        textSize = 15f
        setSingleLine(true)
        setTextColor(textColor)
        setHintTextColor(muted)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = rounded(Color.parseColor("#F3F4F6"), 12)
    }

    private fun primaryButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 15f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(primary, 14)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(46)
        ).apply { topMargin = dp(6) }
    }

    private fun secondaryButton(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 14f
        isAllCaps = false
        setTextColor(primary)
        background = rounded(primarySoft, 14)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(44)
        ).apply { topMargin = dp(8) }
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
