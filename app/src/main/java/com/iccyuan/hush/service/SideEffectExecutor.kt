package com.iccyuan.hush.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.widget.Toast
import com.iccyuan.hush.data.model.HttpMethod
import com.iccyuan.hush.engine.SideEffect
import com.iccyuan.hush.engine.VariableStore
import com.iccyuan.hush.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/**
 * 执行由引擎产生的、依赖上下文且不需要原始
 * [android.service.notification.StatusBarNotification] 的副作用。
 * 自动回复由 [AutoReplyHelper] 单独处理，因为它需要源通知的 RemoteInput。
 */
class SideEffectExecutor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val tts: TtsManager,
) {
    fun execute(effects: List<SideEffect>) {
        effects.forEach { effect ->
            when (effect) {
                is SideEffect.ReadAloud -> tts.speak(effect.text)
                is SideEffect.WakeScreen -> wakeScreen(effect.durationMs)
                is SideEffect.Toast -> showToast(effect.text)
                is SideEffect.RunTasker -> runTasker(effect.taskName)
                is SideEffect.Webhook -> fireWebhook(effect.url, effect.method, effect.body)
                is SideEffect.MuteApp ->
                    VariableStore.muteApp(effect.pkg, effect.ruleId)
                is SideEffect.Danmaku ->
                    DanmakuController.show(context, effect.text, effect.durationMs)
                is SideEffect.Digest ->
                    DigestController.add(context, effect.pkg, effect.appName, effect.line, effect.windowMinutes)
                is SideEffect.AutoReply -> Unit // 由服务结合 sbn 处理
            }
        }
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

    private fun showToast(text: String) {
        if (text.isBlank()) return
        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    /** 广播 Tasker 外部任务 intent。需要 Tasker 允许此操作。 */
    private fun runTasker(taskName: String) {
        if (taskName.isBlank()) return
        val intent = Intent("net.dinglisch.android.tasker.ACTION_TASK").apply {
            setPackage(TASKER_PACKAGE)
            putExtra("task_name", taskName)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        runCatching { context.sendBroadcast(intent) }
            .onSuccess { Logger.i("tasker task broadcast: $taskName") }
            .onFailure { Logger.w("tasker task failed: $taskName", it) }
    }

    private fun fireWebhook(url: String, method: HttpMethod, body: String) {
        if (url.isBlank()) return
        scope.launch(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            runCatching {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method.name
                    connectTimeout = WEBHOOK_TIMEOUT_MS
                    readTimeout = WEBHOOK_TIMEOUT_MS
                    if (method != HttpMethod.GET) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    }
                }
                val code = conn!!.responseCode // 强制请求完成
                Logger.i("webhook ${method.name} $url -> $code")
            }.onFailure {
                Logger.w("webhook ${method.name} $url failed: ${it.message}", it)
            }
            // 无论成功失败都释放连接。
            runCatching { conn?.disconnect() }
        }
    }

    private companion object {
        const val TASKER_PACKAGE = "net.dinglisch.android.taskerm"
        const val WEBHOOK_TIMEOUT_MS = 8000
    }
}
