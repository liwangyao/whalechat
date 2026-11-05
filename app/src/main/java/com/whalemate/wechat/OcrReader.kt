package com.whalemate.wechat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

/** 微信屏蔽无障碍树后的兜底：截屏 → 本地中文 OCR → 按左右气泡拼成消息。 */
object OcrReader {
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    private val timeRegex = Regex(
        "^(昨天|今天|前天|星期[一二三四五六日天]|周[一二三四五六日天]|\\d{1,2}:\\d{2}|\\d{1,2}月\\d{1,2}日.*|上午.*|下午.*|凌晨.*)$"
    )

    private val noise = setOf(
        "微信", "通讯录", "发现", "我", "返回", "发送", "表情", "更多", "搜索", "+",
        "按住 说话", "按住说话", "语音", "相册", "拍摄", "视频通话", "位置",
        "红包", "转账", "文件", "收藏", "取消", "确定", "完成", "鲸", "×"
    )

    fun read(ctx: Context, bitmap: Bitmap, contact: String, onDone: (WeChatScreen) -> Unit) {
        val width = bitmap.width
        val height = bitmap.height
        val density = ctx.resources.displayMetrics.density
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { text ->
                val lines = ArrayList<Pair<Rect, String>>()
                for (block in text.textBlocks) {
                    for (line in block.lines) {
                        val t = line.text.trim()
                        if (t.isEmpty()) continue
                        val box = line.boundingBox ?: continue
                        lines.add(box to t)
                    }
                }

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

                onDone(WeChatScreen(messages.isNotEmpty(), contact, messages.takeLast(12)))
            }
            .addOnFailureListener {
                onDone(WeChatScreen(false, contact, emptyList()))
            }
    }
}
