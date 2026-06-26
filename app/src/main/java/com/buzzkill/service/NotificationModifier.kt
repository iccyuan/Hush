package com.buzzkill.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import com.buzzkill.R
import com.buzzkill.data.model.NotificationField
import com.buzzkill.engine.Decision

/**
 * 将 [Decision] 转化为具体的通知操作。由于 NotificationListenerService 无法就地编辑
 * 其他应用的通知，因此会在我们自己的通知渠道下重建一条经过修改的通知（以便我们控制提醒方式），
 * 取消原通知，并重新发布该副本——同时保留图标、intent、操作和样式。原应用的名称会通过
 * 替代名称（substitute-name）附加项显示，使重建后的通知看起来仍然是原生的。
 */
class NotificationModifier(
    private val context: Context,
    private val channels: ChannelManager,
) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    /** 由原始 key 派生的稳定通知 id，使更新能够干净地替换原通知。 */
    private fun notifyId(sbn: StatusBarNotification): Int = sbn.key.hashCode()

    fun repost(sbn: StatusBarNotification, decision: Decision, appName: String) {
        val original = sbn.notification
        val extras = original.extras

        val title = decision.fieldEdits[NotificationField.TITLE]
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = decision.fieldEdits[NotificationField.TEXT]
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = decision.fieldEdits[NotificationField.BIG_TEXT]
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = decision.fieldEdits[NotificationField.SUB_TEXT]
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        val channelId = channels.channelFor(decision.importance, decision.sound, decision.bypassDnd)

        val builder = Notification.Builder(context, channelId).apply {
            setSmallIcon(original.smallIcon ?: Icon.createWithResource(context, R.drawable.ic_stat_buzzkill))
            title?.let { setContentTitle(it) }
            text?.let { setContentText(it) }
            subText?.let { setSubText(it) }
            if (!bigText.isNullOrEmpty()) {
                setStyle(Notification.BigTextStyle().bigText(bigText))
            }
            original.getLargeIcon()?.let { setLargeIcon(it) }
            original.contentIntent?.let { setContentIntent(it) }
            original.deleteIntent?.let { setDeleteIntent(it) }
            setAutoCancel((original.flags and Notification.FLAG_AUTO_CANCEL) != 0)
            setWhen(original.`when`)
            setShowWhen(true)
            if (original.color != 0) setColor(original.color)
            original.group?.let { setGroup(it) }
            // 保留交互式操作（回复、标记为已读等）。
            original.actions?.forEach { addAction(it) }
            // 使重建后的通知仍然显示为源应用。绕过勿扰模式和重要性由所选通知渠道
            // 控制，而非由 builder 控制。
            addExtras(android.os.Bundle().apply {
                // （隐藏的）EXTRA_SUBSTITUTE_APP_NAME 常量的公开键值。
                putString("android.substName", appName)
            })
        }

        // 发布我们重建的副本。服务会通过 cancelNotification(key) 取消源通知——
        // 只有监听器才能移除其他应用发布的通知。
        nm.notify(notifyId(sbn), builder.build())
    }

    /** 移除我们先前重新发布的副本（例如当其源通知被移除时）。 */
    fun cancelReposted(sbn: StatusBarNotification) {
        nm.cancel(notifyId(sbn))
    }
}
