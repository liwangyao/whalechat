package com.whalemate.wechat

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class ChatMessage(val fromMe: Boolean, val text: String)
data class WeChatScreen(val isChat: Boolean, val title: String, val messages: List<ChatMessage>)

object WeChatParser {
    private data class Item(val text: String, val rect: Rect, val editable: Boolean)
    private data class Row(val top: Int, val msg: ChatMessage)

    private val timeRegex = Regex(
        "^(昨天|今天|前天|星期[一二三四五六日天]|周[一二三四五六日天]|\\d{1,2}:\\d{2}|\\d{1,2}月\\d{1,2}日.*|上午.*|下午.*|凌晨.*)$"
    )

    private val noise = setOf(
        "微信", "通讯录", "发现", "我", "返回", "发送", "表情", "更多", "搜索", "+",
        "按住 说话", "按住说话", "语音", "相册", "拍摄", "视频通话", "位置",
        "红包", "转账", "文件", "收藏", "取消", "确定", "完成"
    )

    fun parse(
        root: AccessibilityNodeInfo,
        width: Int,
        height: Int,
        density: Float,
        contactName: String
    ): WeChatScreen {
        val items = ArrayList<Item>()
        val edits = ArrayList<AccessibilityNodeInfo>()
        walk(root, items, edits, 0)

        val edit = edits.firstOrNull()
        val inputTop = if (edit != null) {
            val inputRect = Rect()
            edit.getBoundsInScreen(inputRect)
            inputRect.top
        } else {
            (height * 0.86f).toInt()
        }

        val minTop = (100 * density).toInt()
        val name = contactName.trim()
        val rows = ArrayList<Row>()
        val seen = HashSet<String>()
        var hasLeft = false
        var hasRight = false

        for (item in items) {
            if (item.editable) continue
            val t = item.text.trim()
            if (t.isEmpty() || t.length > 200) continue
            if (t in noise) continue
            if (timeRegex.matches(t)) continue
            if (name.isNotEmpty() && t == name) continue
            if (t.contains("对方正在输入")) continue
            if (t.contains("撤回了一条消息")) continue
            if (item.rect.top < minTop) continue
            if (item.rect.bottom > inputTop) continue

            val key = t + "@" + item.rect.left + "," + item.rect.top
            if (!seen.add(key)) continue

            val centerX = (item.rect.left + item.rect.right) / 2
            val fromMe = centerX > width * 0.55f
            if (fromMe) hasRight = true else hasLeft = true
            rows.add(Row(item.rect.top, ChatMessage(fromMe, t)))
        }

        rows.sortBy { it.top }

        val isChat = rows.isNotEmpty() && (edit != null || (hasLeft && hasRight))
        return WeChatScreen(isChat, name, rows.map { it.msg })
    }

    private fun walk(
        node: AccessibilityNodeInfo?,
        out: MutableList<Item>,
        edits: MutableList<AccessibilityNodeInfo>,
        depth: Int
    ) {
        if (node == null || depth > 40) return
        val cls = node.className?.toString() ?: ""
        val editable = node.isEditable || cls.contains("EditText")
        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""

        if (text.isNotEmpty() || editable) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            out.add(Item(text, rect, editable))
            if (editable) edits.add(node)
        }

        for (i in 0 until node.childCount) {
            walk(node.getChild(i), out, edits, depth + 1)
        }
    }
}
