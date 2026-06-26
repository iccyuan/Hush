package com.buzzkill.service

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import com.buzzkill.data.model.NotificationField

/** 从已发布的通知中提取可编辑的文本字段。 */
object NotificationFields {

    fun extract(sbn: StatusBarNotification): MutableMap<NotificationField, String> {
        val extras = sbn.notification.extras
        val map = mutableMapOf<NotificationField, String>()
        fun put(field: NotificationField, value: CharSequence?) {
            value?.toString()?.takeIf { it.isNotEmpty() }?.let { map[field] = it }
        }
        put(NotificationField.TITLE, extras.getCharSequence(Notification.EXTRA_TITLE))
        put(NotificationField.TEXT, extras.getCharSequence(Notification.EXTRA_TEXT))
        put(NotificationField.BIG_TEXT, extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        put(NotificationField.SUB_TEXT, extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        put(NotificationField.INFO_TEXT, extras.getCharSequence(Notification.EXTRA_INFO_TEXT))
        put(NotificationField.TICKER, sbn.notification.tickerText)
        return map
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
