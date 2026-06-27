package com.buzzkill.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.buzzkill.data.RuleRepository
import com.buzzkill.data.SettingsStore
import com.buzzkill.data.model.Rule
import com.buzzkill.engine.Decision
import com.buzzkill.engine.MatchContext
import com.buzzkill.engine.RuleEngine
import com.buzzkill.engine.SideEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 实时入口点：接收每一条发布的通知，通过 [RuleEngine] 对其进行处理，
 * 并应用得到的 [Decision]（丢弃 / 重新发布 / 移除 / 暂缓 / 副作用）。
 *
 * 活动规则和总开关被镜像到内存中，因此热路径永远不会访问数据库。
 */
class BuzzKillListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = RuleEngine()

    private lateinit var repository: RuleRepository
    private lateinit var logRepository: com.buzzkill.data.NotificationLogRepository
    private lateinit var settings: SettingsStore
    private lateinit var channels: ChannelManager
    private lateinit var modifier: NotificationModifier
    private lateinit var tts: TtsManager
    private lateinit var sideEffects: SideEffectExecutor

    @Volatile private var activeRules: List<Rule> = emptyList()
    @Volatile private var masterEnabled: Boolean = true
    @Volatile private var logActivity: Boolean = true
    @Volatile private var connected: Boolean = false

    override fun onCreate() {
        super.onCreate()
        repository = RuleRepository.get(this)
        logRepository = com.buzzkill.data.NotificationLogRepository.get(this)
        settings = SettingsStore.get(this)
        channels = ChannelManager(this)
        modifier = NotificationModifier(this, channels)
        tts = TtsManager(this)
        sideEffects = SideEffectExecutor(this, scope, tts)
        channels.ensureBaseChannels()
        // 恢复并持久化运行时状态（冷却 / 静音 / 变量），使其跨进程重启依然有效。
        com.buzzkill.data.RuntimeStateStore.init(this)

        scope.launch {
            repository.observeAll().collectLatest {
                activeRules = it.filter(Rule::enabled)
                Log.i(TAG, "rules loaded: ${activeRules.size} enabled of ${it.size}")
            }
        }
        scope.launch {
            settings.masterEnabled.collectLatest {
                masterEnabled = it
                // 关闭总开关同时解除所有应用静音。
                if (!it) com.buzzkill.engine.VariableStore.unmuteAll()
            }
        }
        scope.launch { settings.logActivity.collectLatest { logActivity = it } }
        Log.i(TAG, "service onCreate")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        channels.ensureBaseChannels()
        instance = this
        Log.i(TAG, "listener connected; activeRules=${activeRules.size}")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.i(TAG, "posted from ${sbn.packageName}; master=$masterEnabled rules=${activeRules.size}")
        if (!masterEnabled) return
        // 切勿处理我们自己重新发布的副本——否则会陷入无限循环。
        if (sbn.packageName == packageName) return

        scope.launch {
            try {
                process(sbn)
            } catch (t: Throwable) {
                Log.e(TAG, "process failed for ${sbn.packageName}", t)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        // 当源通知被移除（用户清除或应用自行撤回）时，连带移除我们为其重新发布的副本，
        // 否则改写后的副本会滞留在通知栏。我们自己重发的副本由本应用拥有，跳过它以免误删/递归。
        if (sbn.packageName == packageName) return
        if (::modifier.isInitialized) modifier.cancelReposted(sbn)
    }

    private suspend fun process(sbn: StatusBarNotification) {
        val device = DeviceState.sample(this)
        val appName = NotificationFields.appLabel(this, sbn.packageName)
        val ctx = MatchContext(
            packageName = sbn.packageName,
            appName = appName,
            fields = NotificationFields.extract(sbn),
            isOngoing = sbn.isOngoing,
            hasReply = NotificationFields.hasReplyAction(sbn),
            device = device,
        )

        val decision = engine.evaluate(ctx, activeRules)
        if (decision.matched) {
            applyDecision(sbn, decision, appName)
            recordFires(decision)
        }
        // 常驻通知（音乐、下载、前台服务、VPN 等）不写入历史，避免把历史刷满。
        // isOngoing 只覆盖 FLAG_ONGOING_EVENT；像 VPN 这类只设了 FLAG_NO_CLEAR
        // 的不可清除通知也算常驻，一并跳过。规则仍会照常对其求值——这里只跳过记录。
        val isPersistent = sbn.isOngoing ||
            (sbn.notification.flags and android.app.Notification.FLAG_NO_CLEAR) != 0
        if (logActivity && !isPersistent) logNotification(sbn, appName, decision)
    }

    private suspend fun logNotification(sbn: StatusBarNotification, appName: String, decision: Decision) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        val outcome = when {
            decision.discard -> com.buzzkill.data.model.NotificationLog.OUTCOME_DISCARDED
            decision.needsRepost -> com.buzzkill.data.model.NotificationLog.OUTCOME_MODIFIED
            decision.snoozeMinutes != null -> com.buzzkill.data.model.NotificationLog.OUTCOME_SNOOZED
            decision.dismiss -> com.buzzkill.data.model.NotificationLog.OUTCOME_DISMISSED
            else -> com.buzzkill.data.model.NotificationLog.OUTCOME_NONE
        }
        runCatching {
            logRepository.add(
                com.buzzkill.data.model.NotificationLog(
                    time = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    packageName = sbn.packageName,
                    appName = appName,
                    title = title,
                    text = text,
                    matched = decision.matched,
                    firedRuleIds = decision.firedRuleIds.joinToString(","),
                    outcome = outcome,
                )
            )
        }
    }

    private fun applyDecision(sbn: StatusBarNotification, decision: Decision, appName: String) {
        // 自动回复需要原始通知的 RemoteInput。
        decision.sideEffects.filterIsInstance<SideEffect.AutoReply>().forEach {
            AutoReplyHelper.reply(this, sbn, it.message)
        }
        sideEffects.execute(decision.sideEffects)

        // 弹幕会替代原生通知——但只有在弹幕确实能够显示（已授予悬浮窗权限）时
        // 才抑制原生通知，否则通知会悄无声息地消失。
        if (decision.sideEffects.any { it is SideEffect.Danmaku } && DanmakuController.canShow(this)) {
            safeCancel(sbn.key)
        }

        when {
            decision.discard -> safeCancel(sbn.key)
            decision.needsRepost -> {
                modifier.repost(sbn, decision, appName)
                // 移除源通知；由我们重建的副本取而代之。
                safeCancel(sbn.key)
            }
        }

        decision.snoozeMinutes?.let { minutes ->
            runCatching { snoozeNotification(sbn.key, minutes * 60_000L) }
        }

        if (decision.dismiss) {
            if (decision.dismissDelayMs > 0) {
                scope.launch {
                    delay(decision.dismissDelayMs)
                    safeCancel(sbn.key)
                }
            } else {
                safeCancel(sbn.key)
            }
        }
    }

    private fun safeCancel(key: String) {
        runCatching { cancelNotification(key) }
    }

    private fun recordFires(decision: Decision) {
        if (decision.firedRuleIds.isEmpty()) return
        scope.launch {
            decision.firedRuleIds.forEach { repository.incrementFireCount(it) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        scope.cancel()
    }

    companion object {
        private const val TAG = "BuzzKill"

        /** 当监听器处于连接状态时非空；供 UI 用于显示状态。 */
        @Volatile
        var instance: BuzzKillListenerService? = null
            private set

        fun isConnected(): Boolean = instance != null
    }
}
