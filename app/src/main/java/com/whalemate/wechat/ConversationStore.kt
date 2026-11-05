package com.whalemate.wechat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 每个联系人一份最近对话，跨多次分析累积，避免只能看到屏幕最新一条。 */
object ConversationStore {
    private const val PREFS = "whalechat_conv"
    private const val MAX = 40

    fun load(ctx: Context, contact: String): List<ChatMessage> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(contact), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<ChatMessage>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(ChatMessage(o.optBoolean("me", false), o.optString("text", "")))
            }
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun save(ctx: Context, contact: String, messages: List<ChatMessage>) {
        val arr = JSONArray()
        for (message in messages.takeLast(MAX)) {
            arr.put(JSONObject().put("me", message.fromMe).put("text", message.text))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(contact), arr.toString())
            .apply()
    }

    fun merge(old: List<ChatMessage>, new: List<ChatMessage>): List<ChatMessage> {
        if (old.isEmpty()) return new.takeLast(MAX)
        if (new.isEmpty()) return old.takeLast(MAX)

        val lastOld = old.last()
        var start = 0
        for (i in new.indices.reversed()) {
            if (new[i].text == lastOld.text && new[i].fromMe == lastOld.fromMe) {
                start = i + 1
                break
            }
        }
        return (old + new.drop(start)).takeLast(MAX)
    }

    private fun key(contact: String): String = "c_" + contact.ifBlank { "default" }
}
