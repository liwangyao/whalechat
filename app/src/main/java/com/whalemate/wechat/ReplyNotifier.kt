package com.whalemate.wechat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** 没有悬浮窗权限时，用通知栏三条动作代替。 */
object ReplyNotifier {
    private const val CHANNEL = "replies"

    fun show(ctx: Context, replies: List<ReplyOption>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "回复候选", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val builder = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("鲸聊 · 选一条发送")
            .setContentText(replies.take(3).joinToString(" / ") { it.text })
            .setStyle(
                Notification.BigTextStyle().bigText(
                    replies.take(3).joinToString("\n") { "• ${it.text}" }
                )
            )
            .setAutoCancel(true)

        replies.take(3).forEachIndexed { index, reply ->
            val intent = Intent(ctx, ReplyActionReceiver::class.java).putExtra("index", index)
            val pending = PendingIntent.getBroadcast(
                ctx,
                100 + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val label = "发：" + reply.text.take(8) + if (reply.text.length > 8) "…" else ""
            builder.addAction(
                Notification.Action.Builder(android.R.drawable.ic_menu_send, label, pending).build()
            )
        }

        manager.notify(1001, builder.build())
    }
}
