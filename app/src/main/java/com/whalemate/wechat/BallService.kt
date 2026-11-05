package com.whalemate.wechat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast

/** 独立的前台服务：不依赖无障碍也能把球挂在屏幕上。 */
class BallService : Service() {

    companion object {
        private const val CHANNEL = "ball"

        fun start(ctx: Context) {
            val intent = Intent(ctx, BallService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(intent)
            else ctx.startService(intent)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, BallService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "鲸聊悬浮球", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("鲸聊悬浮球运行中")
            .setContentText("点球分析；点球右上角 × 关闭")
            .setOngoing(true)
            .build()
        startForeground(2001, notification)

        FloatingBall.show(
            this,
            onTap = { showModes() },
            onClose = { stopSelf() }
        )
    }

    private fun showModes() {
        val service = ChatA11yService.instance
        if (service == null) {
            Toast.makeText(this, "无障碍没开：开启后才能读屏", Toast.LENGTH_SHORT).show()
            return
        }
        service.showModePicker()
    }

    override fun onDestroy() {
        FloatingBall.hide()
        AppSettings(this).showBall = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
