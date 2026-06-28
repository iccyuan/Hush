package com.iccyuan.hush.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.iccyuan.hush.R
import com.iccyuan.hush.data.model.NotificationField
import com.iccyuan.hush.engine.Decision

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

        fun edited(field: NotificationField) = decision.fieldEdits[field]
        val title = edited(NotificationField.TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = edited(NotificationField.TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = edited(NotificationField.BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = edited(NotificationField.SUB_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val infoText = edited(NotificationField.INFO_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()
        val ticker = edited(NotificationField.TICKER)
            ?: original.tickerText?.toString()

        val channelId = channels.channelFor(decision.importance, decision.sound, decision.bypassDnd)

        val builder = Notification.Builder(context, channelId).apply {
            // 先以原通知的全部 extras 作为基底——这样 MessagingStyle / InboxStyle / 媒体样式
            // 所依赖的模板与消息数据（android.messages / android.textLines / android.template 等）
            // 以及 people、progress 等附加项都会被保留。随后的字段覆盖写在其后，确保能盖过原值。
            addExtras(Bundle(extras))
            setSmallIcon(original.smallIcon ?: Icon.createWithResource(context, R.drawable.ic_stat_hush))
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

            // 覆盖编辑过/解析出的文本字段（位于 addExtras 之后以确保生效）。
            title?.let { setContentTitle(it) }
            text?.let { setContentText(it) }
            subText?.let { setSubText(it) }
            ticker?.let { setTicker(it) }
            // INFO_TEXT 自 O 起没有公开 setter（其角色由通知渠道接管），直接写入 extras 键。
            infoText?.let { addExtras(Bundle().apply { putCharSequence(Notification.EXTRA_INFO_TEXT, it) }) }
            // 仅当存在展开文本时才套用 BigTextStyle；否则保留上面继承下来的原始样式。
            if (!bigText.isNullOrEmpty()) {
                setStyle(Notification.BigTextStyle().bigText(bigText))
            }
            // 使重建后的通知仍然显示为源应用。绕过勿扰模式和重要性由所选通知渠道
            // 控制，而非由 builder 控制。
            addExtras(Bundle().apply {
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
