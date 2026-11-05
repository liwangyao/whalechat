package com.whalemate.wechat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReplyActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val index = intent.getIntExtra("index", -1)
        if (index >= 0) {
            ChatA11yService.instance?.sendByIndex(index)
        }
    }
}
