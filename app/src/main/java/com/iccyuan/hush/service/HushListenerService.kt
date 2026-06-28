package com.iccyuan.hush.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.iccyuan.hush.data.NotificationLogRepository
import com.iccyuan.hush.data.RuleRepository
import com.iccyuan.hush.data.RuntimeStateStore
import com.iccyuan.hush.data.SettingsStore
import com.iccyuan.hush.data.model.Condition
import com.iccyuan.hush.data.model.NotificationLog
import com.iccyuan.hush.data.model.Rule
import com.iccyuan.hush.engine.Decision
import com.iccyuan.hush.engine.MatchContext
import com.iccyuan.hush.engine.RuleEngine
import com.iccyuan.hush.engine.SideEffect
import com.iccyuan.hush.engine.TemplateEngine
import com.iccyuan.hush.engine.VariableStore
import com.iccyuan.hush.util.Logger
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
class HushListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = RuleEngine()

    private lateinit var repository: RuleRepository
    private lateinit var logRepository: NotificationLogRepository
    private lateinit var settings: SettingsStore
    private lateinit var channels: ChannelManager
    private lateinit var modifier: NotificationModifier
    private lateinit var tts: TtsManager
    private lateinit var sideEffects: SideEffectExecutor

    @Volatile private var activeRules: List<Rule> = emptyList()
    @Volatile private var masterEnabled: Boolean = true
    @Volatile private var logActivity: Boolean = true
    @Volatile private var immersiveDanmaku: Boolean = false
    @Volatile private var connected: Boolean = false
    // 仅当有启用规则用到对应条件时才置 true，避免每条通知都白白探测耳机/网络状态。
    @Volatile private var needsHeadphones: Boolean = false
    @Volatile private var needsWifi: Boolean = false

    /** 更新内存中的活动规则，并据此重算需要采样哪些设备状态。 */
    private fun setActiveRules(rules: List<Rule>) {
        activeRules = rules
        needsHeadphones = rules.any { r -> r.conditions.any { it is Condition.HeadphonesCondition } }
        needsWifi = rules.any { r -> r.conditions.any { it is Condition.WifiCondition } }
    }

    override fun onCreate() {
        super.onCreate()
        repository = RuleRepository.get(this)
        logRepository = NotificationLogRepository.get(this)
        settings = SettingsStore.get(this)
        channels = ChannelManager(this)
        modifier = NotificationModifier(this, channels)
        tts = TtsManager(this)
        sideEffects = SideEffectExecutor(this, scope, tts)
        channels.ensureBaseChannels()
        // 恢复并持久化运行时状态（冷却 / 静音 / 变量），使其跨进程重启依然有效。
        RuntimeStateStore.init(this)

        scope.launch {
            repository.observeAll().collectLatest {
                setActiveRules(it.filter(Rule::enabled))
                Logger.i("rules loaded: ${activeRules.size} enabled of ${it.size}")
            }
        }
        scope.launch {
            settings.masterEnabled.collectLatest {
                masterEnabled = it
                // 关闭总开关同时解除所有应用静音。
                if (!it) VariableStore.unmuteAll()
            }
        }
        scope.launch { settings.logActivity.collectLatest { logActivity = it } }
        scope.launch { settings.immersiveDanmaku.collectLatest { immersiveDanmaku = it } }
        Logger.i("service onCreate")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        channels.ensureBaseChannels()
        instance = this
        // 重新绑定后立即同步拉取一次最新规则：onCreate 里的 observeAll 首次发射有时机不确定性，
        // 重连场景下若不主动刷新，可能继续用旧规则——表现为「改了规则要重开开关才生效」。
        scope.launch {
            setActiveRules(repository.enabledRules())
            Logger.i("listener connected; rules refreshed: ${activeRules.size}")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
        instance = null
        // 部分 OEM 的省电策略会杀掉监听器且不再自动恢复，导致漏掉大量通知。
        // 主动请求系统重新绑定，尽快恢复连接。
        runCatching {
            requestRebind(android.content.ComponentName(this, HushListenerService::class.java))
        }
        Logger.w("listener disconnected; requested rebind")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Logger.i("posted from ${sbn.packageName}; master=$masterEnabled rules=${activeRules.size}")
        if (!masterEnabled) return
        // 切勿处理我们自己重新发布的副本——否则会陷入无限循环。
        if (sbn.packageName == packageName) return

        scope.launch {
            try {
                process(sbn)
            } catch (t: Throwable) {
                Logger.e("process failed for ${sbn.packageName}", t)
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
        val device = DeviceState.sample(this, needsHeadphones, needsWifi)
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

        // 沉浸弹幕：开启且当前处于全屏（横屏看视频/玩游戏）时，把原本仍会原生弹出的通知
        // 改为弹幕呈现——强制丢弃原生通知并注入一条弹幕，交由下方 discard 路径统一处理。
        if (immersiveDanmaku && !decision.discard && isFullscreen() && DanmakuController.canShow(this)) {
            decision.sideEffects.add(
                SideEffect.Danmaku(
                    TemplateEngine.render("{app}: {title} {text}", ctx),
                    7000L,
                )
            )
            decision.discard = true
            decision.matched = true
        }

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
            decision.discard -> NotificationLog.OUTCOME_DISCARDED
            decision.needsRepost -> NotificationLog.OUTCOME_MODIFIED
            decision.snoozeMinutes != null -> NotificationLog.OUTCOME_SNOOZED
            decision.dismiss -> NotificationLog.OUTCOME_DISMISSED
            else -> NotificationLog.OUTCOME_NONE
        }
        runCatching {
            logRepository.add(
                NotificationLog(
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

    /**
     * 全屏检测（沉浸弹幕用）。无系统 API 可从后台服务直接读取「沉浸/全屏」状态，
     * 这里用「屏幕点亮 + 横屏」作为务实的近似——覆盖最常见的横屏看视频、玩游戏场景。
     * 竖屏全屏视频暂不计入。
     */
    private fun isFullscreen(): Boolean {
        val landscape = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val pm = getSystemService(android.os.PowerManager::class.java)
        val interactive = pm?.isInteractive == true
        return interactive && landscape
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

        /** 当监听器处于连接状态时非空；供 UI 用于显示状态。 */
        @Volatile
        var instance: HushListenerService? = null
            private set

        fun isConnected(): Boolean = instance != null

        /** 当已授权但监听器未连接时，请求系统重新绑定（从 UI 打开时调用以加速恢复）。 */
        fun requestRebindIfNeeded(context: android.content.Context) {
            if (isConnected()) return
            if (!NotificationAccess.isGranted(context)) return
            runCatching {
                NotificationListenerService.requestRebind(
                    android.content.ComponentName(context, HushListenerService::class.java)
                )
            }
        }
    }
}
