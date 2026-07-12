package com.iccyuan.notifytest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * 模拟 VPN / 播放器那种**常驻通知**：前台服务，每秒重发同一条通知来刷新里面的数字。
 *
 * 必须走前台服务——普通应用调 `setOngoing(true)` 会被系统直接忽略（flags 仍是 0），
 * 造不出真正的常驻通知，也就测不到 Hush 对这类通知的求值节流。
 */
class OngoingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var ticks = 0

    private val tick = object : Runnable {
        override fun run() {
            ticks++
            notificationManager().notify(NOTIFICATION_ID, buildNotification())
            // 打日志才能分清「服务没在刷新」和「刷新了但监听器收不到」——两者现象一样。
            Logger.i("ongoing tick #$ticks")
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    private fun buildNotification(): Notification {
        val nm = notificationManager()
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "常驻测试", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("常驻通知（模拟 VPN）")
            .setContentText("已刷新 $ticks 次 · 流量 ${ticks * 37} KB")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ongoing"
        private const val NOTIFICATION_ID = 42

        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, OngoingService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, OngoingService::class.java))
    }
}
