package com.whalemate.wechat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 回复生成：两种后端。
 *  - direct：直接调 OpenAI 兼容接口（DeepSeek），需要 App 里填 key。
 *  - dsh：调「鲸聊桥」服务（我这边跑），技能/提示词/模型都在服务端，App 只发上下文。
 */
object ReplyEngine {
    fun generate(ctx: Context, settings: AppSettings, screen: WeChatScreen): BrainResult {
        if (settings.backend == "dsh") {
            try {
                return viaDsh(settings, screen)
            } catch (t: Throwable) {
                if (settings.apiKey.isNotBlank()) return viaDirect(ctx, settings, screen)
                throw RuntimeException("桥服务不可用：${t.message}")
            }
        }
        return viaDirect(ctx, settings, screen)
    }

    private fun viaDirect(ctx: Context, settings: AppSettings, screen: WeChatScreen): BrainResult {
        if (settings.apiKey.isBlank()) throw RuntimeException("先在设置里填 DeepSeek API Key")
        val prompt = PromptBuilder.build(ctx, settings, screen)
        val raw = AiClient.chat(settings, prompt.system, prompt.user)
        return PromptBuilder.parseResult(raw)
    }

    private fun viaDsh(settings: AppSettings, screen: WeChatScreen): BrainResult {
        val base = settings.dshUrl.trimEnd('/')
        if (base.isBlank()) throw RuntimeException("桥服务地址为空")
        val url = URL("$base/whalechat/api/reply")

        val body = JSONObject().apply {
            put("mode", settings.mode)
            put("stage", settings.stage)
            put("contact", settings.contactName)
            put("notes", settings.notes)
            put("messages", JSONArray().apply {
                for (m in screen.messages.takeLast(12)) {
                    put(JSONObject().put("fromMe", m.fromMe).put("text", m.text))
                }
            })
        }.toString()

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (settings.dshToken.isNotBlank()) {
                setRequestProperty("X-Whalechat-Token", settings.dshToken)
            }
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        conn.disconnect()
        if (code !in 200..299) throw RuntimeException("HTTP $code: ${text.take(160)}")

        val obj = JSONObject(text)
        val analysis = obj.optString("analysis", "").trim()
        val arr = obj.optJSONArray("options") ?: throw RuntimeException("桥没有返回 options")
        val out = ArrayList<ReplyOption>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val t = o.optString("text", "").trim()
            if (t.isNotEmpty()) out.add(ReplyOption(o.optString("tone", ""), t, o.optString("prediction", "")))
        }
        if (out.isEmpty()) throw RuntimeException("桥没有给出可用回复")
        return BrainResult(analysis, out.take(3))
    }
}
