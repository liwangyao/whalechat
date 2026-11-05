package com.whalemate.wechat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import io.github.hzkitty.RapidOCR

/** 截图 → RapidOCR（PaddleOCR ONNX）→ 按左右气泡拼消息。 */
object OcrReader {
    @Volatile
    private var engine: RapidOCR? = null

    private val timeRegex = Regex(
        "^(昨天|今天|前天|星期[一二三四五六日天]|周[一二三四五六日天]|\\d{1,2}:\\d{2}|\\d{1,2}月\\d{1,2}日.*|上午.*|下午.*|凌晨.*)$"
    )

    private val noise = setOf(
        "微信", "通讯录", "发现", "我", "返回", "发送", "表情", "更多", "搜索", "+",
        "按住 说话", "按住说话", "语音", "相册", "拍摄", "视频通话", "位置",
        "红包", "转账", "文件", "收藏", "取消", "确定", "完成", "鲸", "×"
    )

    private fun engine(ctx: Context): RapidOCR {
        engine?.let { return it }
        synchronized(this) {
            engine?.let { return it }
            return RapidOCR.create(ctx).also { engine = it }
        }
    }

    fun read(ctx: Context, bitmap: Bitmap, contact: String, onDone: (WeChatScreen) -> Unit) {
        Thread {
            try {
                val result = engine(ctx).run(bitmap)
                val lines = ArrayList<Pair<Rect, String>>()
                for (rec in result.recRes ?: emptyList()) {
                    val text = rec.text?.trim() ?: continue
                    if (text.isEmpty()) continue
                    if (rec.confidence < 0.4f) continue
                    val box = rec.dtBoxes ?: continue
                    if (box.isEmpty()) continue
                    val xs = box.map { it.x }
                    val ys = box.map { it.y }
                    val left = xs.minOrNull()?.toInt() ?: continue
                    val top = ys.minOrNull()?.toInt() ?: continue
                    val right = xs.maxOrNull()?.toInt() ?: continue
                    val bottom = ys.maxOrNull()?.toInt() ?: continue
                    lines.add(Rect(left, top, right, bottom) to text)
                }
                onDone(buildScreen(lines, bitmap.width, bitmap.height, ctx.resources.displayMetrics.density, contact))
            } catch (_: Throwable) {
                onDone(WeChatScreen(false, contact, emptyList()))
            }
        }.start()
    }

    private fun buildScreen(
        lines: List<Pair<Rect, String>>,
        width: Int,
        height: Int,
        density: Float,
        contact: String
    ): WeChatScreen {
        val minTop = (height * 0.06f).toInt()
        val maxTop = (height * 0.88f).toInt()

        val filtered = lines.filter { (box, t) ->
            box.top in minTop..maxTop &&
                t.length <= 200 &&
                t !in noise &&
                !timeRegex.matches(t) &&
                (contact.isBlank() || t != contact) &&
                !t.contains("撤回了一条消息") &&
                !t.contains("对方正在输入")
        }.sortedBy { it.first.top }

        val messages = ArrayList<ChatMessage>()
        var lastSide = -1
        var lastBottom = -1000
        for ((box, t) in filtered) {
            val side = if ((box.left + box.right) / 2 > width * 0.55f) 1 else 0
            val gap = box.top - lastBottom
            if (messages.isNotEmpty() && side == lastSide && gap < (14 * density).toInt()) {
                val prev = messages.removeAt(messages.size - 1)
                messages.add(ChatMessage(prev.fromMe, prev.text + " " + t))
            } else {
                messages.add(ChatMessage(side == 1, t))
            }
            lastSide = side
            lastBottom = box.bottom
        }

        return WeChatScreen(messages.isNotEmpty(), contact, messages.takeLast(12))
    }
}
