package com.buzzkill.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 系统在重启后会自动重新绑定已启用的 NotificationListenerService，但此接收器为我们提供了
 * 一个钩子，用于重建通知渠道 / 预热状态，从而确保开机后的第一条通知能够无延迟地被处理。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ChannelManager(context).ensureBaseChannels()
    }
}
