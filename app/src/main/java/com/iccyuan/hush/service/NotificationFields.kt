package com.iccyuan.hush.service

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.iccyuan.hush.data.model.NotificationField

/** 从已发布的通知中提取可编辑的文本字段。 */
object NotificationFields {

    fun extract(sbn: StatusBarNotification): MutableMap<NotificationField, String> {
        val notification = sbn.notification
        val extras = notification.extras
        val map = mutableMapOf<NotificationField, String>()
        fun put(field: NotificationField, value: CharSequence?) {
            value?.toString()?.takeIf { it.isNotEmpty() }?.let { map[field] = it }
        }
        put(NotificationField.TITLE, extras.getCharSequence(Notification.EXTRA_TITLE))
        put(NotificationField.TEXT, extras.getCharSequence(Notification.EXTRA_TEXT))
        put(NotificationField.BIG_TEXT, extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        put(NotificationField.SUB_TEXT, extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        put(NotificationField.INFO_TEXT, extras.getCharSequence(Notification.EXTRA_INFO_TEXT))
        put(NotificationField.TICKER, notification.tickerText)
        // 元数据字段：类别 / 渠道 / 发信人——用于更精准的触发器（聊天、邮件、来电等）。
        put(NotificationField.CATEGORY, notification.category)
        put(NotificationField.CHANNEL, notification.channelId)
        put(NotificationField.SENDER, sender(notification))
        return map
    }

    /** best-effort 提取最近一条消息的发信人/会话名（聊天类通知）。 */
    private fun sender(notification: Notification): CharSequence? {
        notification.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.let { return it }
        return runCatching {
            NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(notification)
                ?.messages?.lastOrNull()?.person?.name
        }.getOrNull()
    }

    fun hasReplyAction(sbn: StatusBarNotification): Boolean =
        sbn.notification.actions?.any { it.remoteInputs?.isNotEmpty() == true } == true

    /** 解析面向用户的应用标签，若失败则回退为包名。 */
    fun appLabel(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }
}
