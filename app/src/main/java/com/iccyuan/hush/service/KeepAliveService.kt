package com.iccyuan.hush.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.iccyuan.hush.R
import com.iccyuan.hush.data.HolidayProvider
import com.iccyuan.hush.ui.MainActivity

/**
 * 前台保活服务：用一条常驻通知把进程提升到「前台服务」优先级，显著降低被 OEM 省电策略
 * 杀死的概率——监听器与本服务同进程，因而一并存活。
 *
 * 这条常驻通知同时**显示并可一键切换「今日休息 / 今日工作」**（覆盖节假日判定）。按设计
 * 它始终存在、不提供应用内关闭开关。
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REST -> toggleOverride(HolidayProvider.OVERRIDE_REST)
            ACTION_WORK -> toggleOverride(HolidayProvider.OVERRIDE_WORK)
        }
        startForegroundCompat(buildNotification())
        return START_STICKY
    }

    /** 再次点击当前已激活的那个即清除覆盖（与应用内「今日」卡片一致）。 */
    private fun toggleOverride(target: String) {
        val current = HolidayProvider.todayOverride(this)
        HolidayProvider.setTodayOverride(this, if (current == target) null else target)
    }

    private fun startForegroundCompat(notification: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        }
    }

    private fun buildNotification(): Notification {
        val stateRes = when (HolidayProvider.todayOverride(this)) {
            HolidayProvider.OVERRIDE_REST -> R.string.keepalive_today_rest
            HolidayProvider.OVERRIDE_WORK -> R.string.keepalive_today_work
            else -> R.string.keepalive_today_auto
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, ChannelManager.KEEPALIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_hush)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(stateRes))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .addAction(0, getString(R.string.today_rest), serviceAction(ACTION_REST))
            .addAction(0, getString(R.string.today_work), serviceAction(ACTION_WORK))
            .build()
    }

    private fun serviceAction(action: String): PendingIntent {
        val intent = Intent(this, KeepAliveService::class.java).setAction(action)
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val NOTIF_ID = 7001
        private const val ACTION_REST = "com.iccyuan.hush.action.TODAY_REST"
        private const val ACTION_WORK = "com.iccyuan.hush.action.TODAY_WORK"

        /** 启动（或刷新）保活前台服务。后台启动受限时静默失败，下次打开应用会再尝试。 */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, KeepAliveService::class.java),
                )
            }
        }
    }
}
