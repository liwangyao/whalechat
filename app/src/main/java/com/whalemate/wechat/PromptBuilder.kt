package com.whalemate.wechat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class ReplyOption(val tone: String, val text: String, val prediction: String)
data class Prompt(val system: String, val user: String)

object PromptBuilder {
    private val cache = ConcurrentHashMap<String, String>()

    private fun asset(ctx: Context, name: String): String = cache.getOrPut(name) {
        ctx.assets.open("skills/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun build(ctx: Context, settings: AppSettings, screen: WeChatScreen): Prompt {
        val modeText = when (settings.mode) {
            "goutou" -> asset(ctx, "goutou_skill.md")
            "love" -> asset(ctx, "love_skill.md") + "\n\n" + asset(ctx, "love_playbook.md")
            else -> asset(ctx, "jev_skill.md") + "\n\n" + asset(ctx, "jev_draft.md")
        }

        val system = """
你是「鲸聊」——装在手机上的微信回复助手，替用户起草回复。下面是你必须遵守的工作方法（来自技能库）：

$modeText

输出要求（必须严格遵守）：
- 只输出一个 JSON 对象，不要 markdown 代码块，不要任何解释文字。
- 结构：{"analysis":"一句话判断","options":[{"tone":"稳健|轻松|直接","text":"回复正文","prediction":"她可能怎么回"}]}
- options 给 2~3 条，text 每条不超过 40 个汉字，像真人微信，别堆表情、别堆排比、别写成小作文。
- 不编造事实、记忆、共同经历；不确定就承接或轻轻提问。
        """.trimIndent()

        val stageText = when (settings.stage) {
            1 -> "刚加上"
            3 -> "有好感/暧昧"
            else -> "聊过几次"
        }
        val history = screen.messages.takeLast(12).joinToString("\n") {
            (if (it.fromMe) "我" else "她") + "：" + it.text
        }

        val user = """
对方：${settings.contactName.ifBlank { "当前聊天对象" }}（阶段：$stageText）
备注：${settings.notes.ifBlank { "无" }}
最近对话：
$history

请针对她最新的一条，给出回复建议。
        """.trimIndent()

        return Prompt(system, user)
    }

    fun parseReplies(raw: String): List<ReplyOption> {
        var text = raw.trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw RuntimeException("模型没有返回 JSON")
        text = text.substring(start, end + 1)

        val obj = JSONObject(text)
        val arr: JSONArray = obj.optJSONArray("options") ?: JSONArray()
        val out = ArrayList<ReplyOption>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val t = o.optString("text", "").trim()
            if (t.isNotEmpty()) {
                out.add(ReplyOption(o.optString("tone", ""), t, o.optString("prediction", "")))
            }
        }
        if (out.isEmpty()) {
            val single = obj.optString("reply", "").trim()
            if (single.isNotEmpty()) out.add(ReplyOption("直接", single, ""))
        }
        if (out.isEmpty()) throw RuntimeException("模型没给出可用回复")
        return out.take(3)
    }
}
