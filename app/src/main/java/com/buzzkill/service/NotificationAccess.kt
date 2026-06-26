package com.buzzkill.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** 用于检查和请求通知监听器访问权限的辅助方法。 */
object NotificationAccess {

    fun isGranted(context: Context): Boolean {
        val component = ComponentName(context, BuzzKillListenerService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return enabled.split(":").any {
            ComponentName.unflattenFromString(it) == component
        }
    }

    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
