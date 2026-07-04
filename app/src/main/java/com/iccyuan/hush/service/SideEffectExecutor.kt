package com.iccyuan.hush.service

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.iccyuan.hush.R
import com.iccyuan.hush.data.model.HttpMethod
import com.iccyuan.hush.engine.SideEffect
import com.iccyuan.hush.engine.VariableStore
import com.iccyuan.hush.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 执行由引擎产生的、依赖上下文且不需要原始
 * [android.service.notification.StatusBarNotification] 的副作用。
 * 自动回复由 [AutoReplyHelper] 单独处理，因为它需要源通知的 RemoteInput。
 */
class SideEffectExecutor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val tts: Lazy<TtsManager>,
) {
    fun execute(effects: List<SideEffect>) {
        effects.forEach { effect ->
            when (effect) {
                is SideEffect.ReadAloud -> tts.value.speak(effect.text)
                is SideEffect.WakeScreen -> wakeScreen(effect.durationMs)
                is SideEffect.Toast -> showToast(effect.text)
                is SideEffect.Notify -> postReminder(effect.text)
                is SideEffect.Webhook ->
                    fireWebhook(effect.url, effect.method, effect.params, effect.headers, effect.contentType, effect.body)
                is SideEffect.MuteApp ->
                    VariableStore.muteApp(effect.pkg, effect.ruleId)
                is SideEffect.Danmaku ->
                    DanmakuController.show(context, effect.text)
                is SideEffect.Digest ->
                    DigestController.add(context, effect.pkg, effect.appName, effect.line, effect.windowMinutes)
                is SideEffect.LaunchApp -> launchApp(effect.pkg, effect.activity)
                is SideEffect.RunMacro -> runMacro(effect)
                is SideEffect.AutoReply -> Unit // 由服务结合 sbn 处理
            }
        }
    }

    /** 「打开应用 / 页面」动作：有 activity 用显式 ComponentName，否则用默认启动 Intent。 */
    private fun launchApp(pkg: String, activity: String) {
        if (pkg.isBlank()) return
        val intent = if (activity.isNotBlank()) {
            Intent().setClassName(pkg, activity)
        } else {
            context.packageManager.getLaunchIntentForPackage(pkg)
        }
        if (intent == null) {
            showToast(context.getString(R.string.launch_app_failed))
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                Logger.w("launchApp $pkg/$activity failed: ${it.message}", it)
                showToast(context.getString(R.string.launch_app_failed))
            }
    }

    /** 「运行打卡宏」动作：交给无障碍服务回放；未开启则提示去开启。 */
    private fun runMacro(effect: SideEffect.RunMacro) {
        val svc = HushAccessibilityService.instance
        if (svc == null) {
            showToast(context.getString(R.string.macro_accessibility_off))
            return
        }
        svc.playMacro(effect.steps, effect.screenWidth, effect.screenHeight, effect.repeat)
    }

    @SuppressLint("WakelockTimeout")
    private fun wakeScreen(durationMs: Long) {
        val pm = context.getSystemService(PowerManager::class.java) ?: return
        @Suppress("DEPRECATION")
        val lock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "hush:wake"
        )
        runCatching { lock.acquire(durationMs.coerceIn(500, 30_000)) }
            .onFailure { Logger.w("wakeScreen failed", it) }
    }

    /** 「发送提醒通知」动作：以渲染后的文本发布一条新通知（自带渠道，互不覆盖）。 */
    private fun postReminder(text: String) {
        if (text.isBlank()) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(context, ChannelManager.REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_hush)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { nm.notify(REMINDER_ID_BASE + (text.hashCode() and 0xffff), notification) }
    }

    private fun showToast(text: String) {
        if (text.isBlank()) return
        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun fireWebhook(
        url: String,
        method: HttpMethod,
        params: List<Pair<String, String>>,
        headers: List<Pair<String, String>>,
        contentType: String,
        body: String,
    ) {
        if (url.isBlank()) return
        val fullUrl = appendQuery(url, params)
        scope.launch(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            runCatching {
                conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = method.name
                    connectTimeout = WEBHOOK_TIMEOUT_MS
                    readTimeout = WEBHOOK_TIMEOUT_MS
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                    // 仅当 contentType 非空（即 POST）才携带请求体；GET 不发。
                    if (contentType.isNotEmpty()) {
                        doOutput = true
                        // 用户未在请求头里自定义 Content-Type 时，用所选请求体类型的。
                        if (headers.none { it.first.equals("Content-Type", ignoreCase = true) }) {
                            setRequestProperty("Content-Type", contentType)
                        }
                        outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    }
                }
                val code = conn!!.responseCode // 强制请求完成
                Logger.i("webhook ${method.name} $fullUrl -> $code")
            }.onFailure {
                Logger.w("webhook ${method.name} $fullUrl failed: ${it.message}", it)
            }
            // 无论成功失败都释放连接。
            runCatching { conn?.disconnect() }
        }
    }

    /** 把查询参数（URL 编码后）拼到 URL 上，已有 query 串时用 & 续接。 */
    private fun appendQuery(url: String, params: List<Pair<String, String>>): String {
        if (params.isEmpty()) return url
        val query = params.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val sep = if (url.contains('?')) "&" else "?"
        return "$url$sep$query"
    }

    private companion object {
        const val REMINDER_ID_BASE = 8000
        const val WEBHOOK_TIMEOUT_MS = 8000
    }
}
