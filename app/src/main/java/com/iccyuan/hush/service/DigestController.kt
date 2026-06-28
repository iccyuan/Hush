package com.iccyuan.hush.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import com.iccyuan.hush.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 通知聚合：把命中“聚合”动作的多条通知按应用攒在一个时间窗里，窗口结束时
 * 合并成一条摘要通知发布，从而治理高频应用的刷屏。
 *
 * 每个包独立维护一个缓冲区与一个定时器；窗口内到来的通知只追加，不重置定时器，
 * 因此“每 N 分钟最多一条摘要”。状态保存在内存中——聚合本身就是短时行为。
 */
object DigestController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private class Bucket {
        val lines = ArrayList<String>()
        var appName: String = ""
        var job: Job? = null
    }

    private val buckets = HashMap<String, Bucket>()

    fun add(context: Context, pkg: String, appName: String, line: String, windowMinutes: Int) {
        val app = context.applicationContext
        synchronized(buckets) {
            val bucket = buckets.getOrPut(pkg) { Bucket() }
            bucket.appName = appName.ifBlank { pkg }
            if (line.isNotBlank()) bucket.lines.add(line.trim())
            if (bucket.job == null) {
                val delayMs = windowMinutes.coerceIn(1, 240) * 60_000L
                bucket.job = scope.launch {
                    delay(delayMs)
                    flush(app, pkg)
                }
            }
        }
    }

    private fun flush(context: Context, pkg: String) {
        val (appName, lines) = synchronized(buckets) {
            val bucket = buckets.remove(pkg) ?: return
            bucket.appName to bucket.lines.toList()
        }
        if (lines.isEmpty()) return

        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val style = Notification.InboxStyle().setBigContentTitle(appName)
        // 最多展示最近若干行，避免摘要过长。
        lines.takeLast(8).forEach { style.addLine(it) }
        if (lines.size > 8) style.setSummaryText("+${lines.size - 8}")

        val builder = Notification.Builder(context, ChannelManager.DIGEST_CHANNEL)
            .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_stat_hush))
            .setContentTitle(appName)
            .setContentText(context.getString(R.string.digest_count, lines.size))
            .setStyle(style)
            .setNumber(lines.size)
            .setAutoCancel(true)
            .setGroup("hush_digest_$pkg")

        nm.notify(("digest_$pkg").hashCode(), builder.build())
    }
}
