package com.whalemate.wechat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class ReplyOption(val tone: String, val text: String, val prediction: String)
data class BrainResult(val analysis: String, val options: List<ReplyOption>)
data class Prompt(val system: String, val user: String)

object PromptBuilder {
    private val cache = ConcurrentHashMap<String, String>()

    private fun asset(ctx: Context, name: String): String = cache.getOrPut(name) {
        ctx.assets.open("skills/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private const val EQ_PROMPT = """
你是高情商聊天助手。先接住对方情绪，再回应事实；不急着讲道理，也不教育对方。
回复要短、具体、有分寸：既给对方台阶，也能表达自己的边界。
避免客服腔、心理咨询师腔、套话和过度共情。
"""

    private const val FUN_PROMPT = """
你是轻松幽默的聊天搭子。用具体画面、反差、适度自嘲制造笑点；不油腻、不硬玩梗、不贬低对方。
回复短，像微信里随口一句，允许有表情但别堆。
"""

    private const val DIRECT_PROMPT = """
你是直接真诚的聊天助手。说清楚自己的意思和安排，不绕弯、不试探、不玩欲擒故纵。
给对方明确的回应空间和体面拒绝的余地；一条消息只做一个动作。
"""

    private const val REPAIR_PROMPT = """
你是冲突修复助手。先承认具体事实和对方感受，不空泛道歉、不辩解；给出一个可执行的下一步。
语气克制、真诚，回复短，不用长篇解释。
"""

    private const val TEASE_PROMPT = """
你是“会撩但不油”的聊天搭子。用松弛感、具体画面、轻轻调侃制造一点张力；可以小小推拉，但目的是好玩，不是控制。
底线：不贬低、不打压、不制造虚假稀缺、不玩消失惩罚、不煤气灯、不越过对方明确的拒绝。
调侃之后给台阶；对方不接就收；一条消息只做一个小动作，短句优先。
"""

    private const val PRANK_PROMPT = """
你是“职场整活”助手：把用户的意思写成夸张、好笑的职场话术——离谱画饼、彩虹屁、老板体、阴阳怪气但不人身攻击。
只做玩笑、吐槽和段子；不提供真实的操控、打压、煤气灯、挑拨、欺骗、冒充他人或伪造事实的策略。
输出短、有梗，最好一眼能看出是玩笑；别真教人怎么控制别人。
"""

    fun build(ctx: Context, settings: AppSettings, screen: WeChatScreen): Prompt {
        val custom = settings.customPrompt.trim()
        val modeText = when (settings.mode) {
            "goutou" -> asset(ctx, "goutou_skill.md")
            "love" -> asset(ctx, "love_skill.md") + "\n\n" + asset(ctx, "love_playbook.md")
            "eq" -> EQ_PROMPT
            "fun" -> FUN_PROMPT
            "direct" -> DIRECT_PROMPT
            "repair" -> REPAIR_PROMPT
            "tease" -> TEASE_PROMPT
            "prank" -> PRANK_PROMPT
            "custom" -> custom.ifBlank { asset(ctx, "jev_skill.md") }
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

    fun parseResult(raw: String): BrainResult {
        var text = raw.trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw RuntimeException("模型没有返回 JSON")
        text = text.substring(start, end + 1)

        val obj = JSONObject(text)
        val analysis = obj.optString("analysis", "").trim()

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
        return BrainResult(analysis, out.take(3))
    }
}
