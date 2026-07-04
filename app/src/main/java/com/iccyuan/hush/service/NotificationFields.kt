package com.iccyuan.hush.service

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.iccyuan.hush.data.model.NotificationField
import java.util.concurrent.ConcurrentHashMap

/** 从已发布的通知中提取可编辑的文本字段。 */
object NotificationFields {

    // 每条通知都会解析应用标签，而 PackageManager 的这一调用是 binder 往返，缓存以避免重复开销。
    // 由 [invalidateAppLabel] 在包安装/卸载/更新时按包名失效。
    private val appLabelCache = ConcurrentHashMap<String, String>()

    fun invalidateAppLabel(packageName: String) {
        appLabelCache.remove(packageName)
    }

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

    /**
     * 是否为「常驻通知」——即不该像普通消息那样被处理的持续型通知。这类通知不写入历史、
     * 默认也不触发弹幕（否则 VPN、音乐、下载、来电、导航等会不断刷屏）。
     *
     * 判定综合两类信号，尽量准确：
     *  - **flags**：
     *      · FLAG_ONGOING_EVENT（0x2）——进行中（音乐、录音、正在下载等）。
     *      · FLAG_NO_CLEAR（0x20）——不可一键清除（VPN、部分前台服务、系统常驻）。
     *      · FLAG_FOREGROUND_SERVICE（0x40）——前台服务通知（VPN、下载器、定位、同步等）。
     *  - **category**（应用自报的语义类别）：
     *      · service（后台服务）、transport（媒体播放）、call（通话）、
     *        navigation（导航）、progress（进度/下载）、stopwatch（计时）、alarm（闹钟）。
     *
     * 注：VPN 通知常常只带 NO_CLEAR / FOREGROUND_SERVICE 而**不带** ONGOING_EVENT，
     * 因此仅判断 isOngoing 会漏掉它——这正是「VPN 一直触发弹幕」的根因。
     */
    fun isPersistent(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        val persistentFlags = Notification.FLAG_ONGOING_EVENT or
            Notification.FLAG_NO_CLEAR or
            Notification.FLAG_FOREGROUND_SERVICE
        if (n.flags and persistentFlags != 0) return true
        return when (n.category) {
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_STOPWATCH,
            Notification.CATEGORY_ALARM,
            -> true
            else -> false
        }
    }

    /** 解析面向用户的应用标签，若失败则回退为包名。结果按包名缓存，见 [invalidateAppLabel]。 */
    fun appLabel(context: Context, packageName: String): String =
        appLabelCache.getOrPut(packageName) {
            try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                packageName
            }
        }
}
