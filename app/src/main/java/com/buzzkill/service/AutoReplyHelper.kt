package com.buzzkill.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.service.notification.StatusBarNotification

/**
 * 通过填充并触发消息应用附加在通知上的内联 RemoteInput 操作来发送自动回复。
 * 当通知不带回复操作时，该方法不执行任何操作。
 */
object AutoReplyHelper {

    fun reply(context: Context, sbn: StatusBarNotification, message: String): Boolean {
        if (message.isEmpty()) return false
        val action = findReplyAction(sbn.notification) ?: return false
        val remoteInputs = action.remoteInputs ?: return false

        val intent = Intent()
        val bundle = android.os.Bundle()
        for (remoteInput in remoteInputs) {
            bundle.putCharSequence(remoteInput.resultKey, message)
        }
        RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)

        return try {
            action.actionIntent.send(context, 0, intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun findReplyAction(notification: Notification): Notification.Action? {
        val actions = notification.actions ?: return null
        // 优先选择声明了自由格式文本 RemoteInput 的操作。
        return actions.firstOrNull { action ->
            action.remoteInputs?.any { it.allowFreeFormInput } == true
        } ?: actions.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
    }
}
