package com.buzzkill.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.widget.Toast
import com.buzzkill.data.model.HttpMethod
import com.buzzkill.engine.SideEffect
import com.buzzkill.engine.VariableStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                    VariableStore.muteApp(effect.pkg, nowPlusMinutes(effect.minutes))
                is SideEffect.Danmaku ->
                    DanmakuController.show(context, effect.text, effect.durationMs)
                is SideEffect.AutoReply -> Unit // 由服务结合 sbn 处理
            }
        }
    }

    private fun nowPlusMinutes(minutes: Int) =
        System.currentTimeMillis() + minutes * 60_000L

    @SuppressLint("WakelockTimeout")
    private fun wakeScreen(durationMs: Long) {
        val pm = context.getSystemService(PowerManager::class.java) ?: return
        @Suppress("DEPRECATION")
        val lock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "buzzkill:wake"
        )
        runCatching { lock.acquire(durationMs.coerceIn(500, 30_000)) }
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
            setPackage("net.dinglisch.android.taskerm")
            putExtra("task_name", taskName)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        runCatching { context.sendBroadcast(intent) }
    }

    private fun fireWebhook(url: String, method: HttpMethod, body: String) {
        if (url.isBlank()) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method.name
                    connectTimeout = 8000
                    readTimeout = 8000
                    if (method != HttpMethod.GET) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        withContext(Dispatchers.IO) {
                            outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                        }
                    }
                }
                conn.responseCode // 强制请求完成
                conn.disconnect()
            }
        }
    }
}
