package com.iccyuan.notifytest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * 统一的测试通知发送入口：HIGH 重要性渠道 + 系统默认提示音 + 震动，
 * 即「最吵」的常规通知——用来验证 Hush 的静音/改写规则是否真的掐掉了提醒。
 */
object Notifier {

    const val CHANNEL_ID = "loud"

    private fun ensureChannel(ctx: Context): NotificationManager {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL_ID, "响铃测试", NotificationManager.IMPORTANCE_HIGH)
        ch.enableVibration(true)
        nm.createNotificationChannel(ch)
        return nm
    }

    /**
     * 发送一条测试通知。[tag] 相同则视为同一条的更新；[alertOnce] 为 true 时同 key
     * 更新不再响铃（FLAG_ONLY_ALERT_ONCE），用于验证「静默更新不应被 snooze」。
     */
    fun post(ctx: Context, tag: String, title: String, text: String, alertOnce: Boolean = false) {
        val nm = ensureChannel(ctx)
        val n = Notification.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(alertOnce)
            .build()
        nm.notify(tag, 1, n)
    }
}
