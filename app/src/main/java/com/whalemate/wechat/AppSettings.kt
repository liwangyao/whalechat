package com.whalemate.wechat

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("whalechat", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(value) = sp.edit().putString("api_key", value).apply()

    var baseUrl: String
        get() = sp.getString("base_url", "https://api.deepseek.com") ?: "https://api.deepseek.com"
        set(value) = sp.edit().putString("base_url", value).apply()

    var model: String
        get() = sp.getString("model", "deepseek-chat") ?: "deepseek-chat"
        set(value) = sp.edit().putString("model", value).apply()

    /** jev / goutou / love */
    var mode: String
        get() = sp.getString("mode", "jev") ?: "jev"
        set(value) = sp.edit().putString("mode", value).apply()

    var stage: Int
        get() = sp.getInt("stage", 2)
        set(value) = sp.edit().putInt("stage", value).apply()

    var autoSend: Boolean
        get() = sp.getBoolean("auto_send", false)
        set(value) = sp.edit().putBoolean("auto_send", value).apply()

    var contactName: String
        get() = sp.getString("contact", "") ?: ""
        set(value) = sp.edit().putString("contact", value).apply()

    var notes: String
        get() = sp.getString("notes", "") ?: ""
        set(value) = sp.edit().putString("notes", value).apply()

    /** direct：直连 DeepSeek；dsh：调鲸聊桥服务 */
    var backend: String
        get() = sp.getString("backend", "direct") ?: "direct"
        set(value) = sp.edit().putString("backend", value).apply()

    var dshUrl: String
        get() = sp.getString("dsh_url", "http://127.0.0.1:3220") ?: "http://127.0.0.1:3220"
        set(value) = sp.edit().putString("dsh_url", value).apply()

    var dshToken: String
        get() = sp.getString("dsh_token", "") ?: ""
        set(value) = sp.edit().putString("dsh_token", value).apply()

    /** 常驻悬浮球 */
    var showBall: Boolean
        get() = sp.getBoolean("show_ball", true)
        set(value) = sp.edit().putBoolean("show_ball", value).apply()

    var customName: String
        get() = sp.getString("custom_name", "自定义") ?: "自定义"
        set(value) = sp.edit().putString("custom_name", value).apply()

    var customPrompt: String
        get() = sp.getString("custom_prompt", "") ?: ""
        set(value) = sp.edit().putString("custom_prompt", value).apply()

    /** 收到新消息时自动分析（默认关，手动点球触发） */
    var autoAnalyze: Boolean
        get() = sp.getBoolean("auto_analyze", false)
        set(value) = sp.edit().putBoolean("auto_analyze", value).apply()
}
