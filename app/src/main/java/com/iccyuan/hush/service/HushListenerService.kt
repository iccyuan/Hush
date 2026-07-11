package com.iccyuan.hush.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.iccyuan.hush.data.NotificationLogRepository
import com.iccyuan.hush.data.RuleRepository
import com.iccyuan.hush.data.RuntimeStateStore
import com.iccyuan.hush.data.SettingsStore
import com.iccyuan.hush.data.model.Condition
import com.iccyuan.hush.data.model.DeviceEventType
import com.iccyuan.hush.data.model.Importance
import com.iccyuan.hush.data.model.NotificationLog
import com.iccyuan.hush.data.model.Rule
import com.iccyuan.hush.data.model.Trigger
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
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

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
    // 惰性构造：仅当某条动作真正朗读文本时才绑定 TextToSpeech 引擎，避免大多数从不使用
    // 「朗读」功能的用户在服务启动时白白付出这份开销。
    private val tts = lazy { TtsManager(this) }
    private lateinit var sideEffects: SideEffectExecutor

    @Volatile private var activeRules: List<Rule> = emptyList()
    @Volatile private var masterEnabled: Boolean = true
    @Volatile private var logActivity: Boolean = true
    @Volatile private var immersiveDanmaku: Boolean = false
    @Volatile private var connected: Boolean = false
    // 仅当有启用规则用到对应条件时才置 true，避免每条通知都白白探测耳机/网络/位置状态。
    @Volatile private var needsHeadphones: Boolean = false
    @Volatile private var needsWifi: Boolean = false
    @Volatile private var needsLocation: Boolean = false
    @Volatile private var needsWifiEvents: Boolean = false

    // Wi-Fi 连接/断开事件监听（仅当有事件规则用到时才注册，省资源）。
    private var wifiCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var wifiUp: Boolean = false
    // 最近一次连接的 Wi-Fi SSID：断开事件用它做「指定 Wi-Fi」过滤（断开后已读不到）。
    @Volatile private var lastSsid: String? = null

    // 「就地静音」short-snooze 后系统会把原通知放回并再次回调 onNotificationPosted。
    // 记录这些 key（值为跳过窗口的截止时刻），以便跳过放回的那一次——否则放回的通知会
    // 再次命中规则 → 再次 snooze，循环不止，且副作用（toast/webhook 等）会重复执行。
    private val inPlaceSilenced = ConcurrentHashMap<String, Long>()

    // 应用安装/卸载/更新时，失效对应包名的应用标签缓存（见 [NotificationFields.appLabel]）。
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.data?.schemeSpecificPart?.let { NotificationFields.invalidateAppLabel(it) }
        }
    }

    /** 更新内存中的活动规则，并据此重算需要采样哪些设备状态、同步地理围栏与事件监听。 */
    private fun setActiveRules(rules: List<Rule>) {
        activeRules = rules
        needsHeadphones = rules.any { r -> r.conditions.any { it is Condition.HeadphonesCondition } }
        needsWifi = rules.any { r -> r.conditions.any { it is Condition.WifiCondition } }
        needsLocation = rules.any { r -> r.conditions.any { it is Condition.LocationCondition } }
        needsWifiEvents = rules.any { r ->
            r.triggers.any { t ->
                t is Trigger.DeviceEvent &&
                    (t.event == DeviceEventType.WIFI_CONNECTED || t.event == DeviceEventType.WIFI_DISCONNECTED)
            }
        }
        // 围栏/事件监听同步各自兜底：任一抛异常都不得影响 activeRules 已更新的事实，
        // 更不能让上游规则观察者因此崩溃（否则会退化成「改了规则要重开开关才生效」）。
        runCatching { GeofenceManager.sync(this, rules) }
            .onFailure { Logger.e("geofence sync failed", it) }
        runCatching { syncWifiEventMonitor() }
            .onFailure { Logger.e("wifi monitor sync failed", it) }
    }

    /** 按需注册/注销 Wi-Fi 网络回调；初始状态先 seed，避免注册瞬间把「已连」误报成一次连接事件。 */
    private fun syncWifiEventMonitor() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        if (needsWifiEvents && wifiCallback == null) {
            wifiUp = isWifiConnectedNow(cm)
            if (wifiUp) lastSsid = currentSsid() // seed，供后续断开事件过滤
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (!wifiUp) { wifiUp = true; onWifiEvent(DeviceEventType.WIFI_CONNECTED) }
                }
                override fun onLost(network: Network) {
                    if (wifiUp) { wifiUp = false; onWifiEvent(DeviceEventType.WIFI_DISCONNECTED) }
                }
            }
            val req = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            runCatching { cm.registerNetworkCallback(req, cb); wifiCallback = cb }
                .onFailure { Logger.w("wifi monitor register failed: ${it.message}") }
        } else if (!needsWifiEvents && wifiCallback != null) {
            runCatching { cm.unregisterNetworkCallback(wifiCallback!!) }
            wifiCallback = null
        }
    }

    private fun isWifiConnectedNow(cm: ConnectivityManager): Boolean {
        val n = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(n) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** 读取当前连接的 Wi-Fi SSID；需定位权限 + 定位开启，否则系统返回 <unknown ssid>（此时视为未知）。 */
    private fun currentSsid(): String? = runCatching {
        val wifi = getSystemService(android.net.wifi.WifiManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        val raw = wifi.connectionInfo?.ssid ?: return null
        raw.trim('"').takeIf { it.isNotBlank() && it != "<unknown ssid>" && !it.startsWith("0x") }
    }.getOrNull()

    /** Wi-Fi 连/断的那一刻：评估事件规则并执行其副作用（如发提醒通知）。 */
    private fun onWifiEvent(event: DeviceEventType) {
        if (!masterEnabled) return
        // 连接时读取当前 SSID 并记住；断开时用记住的（断开后已读不到）。供「指定 Wi-Fi」触发器过滤。
        val ssid = when (event) {
            DeviceEventType.WIFI_CONNECTED -> currentSsid()?.also { lastSsid = it }
            DeviceEventType.WIFI_DISCONNECTED -> lastSsid.also { lastSsid = null }
            else -> null
        }
        Logger.i("wifi event: $event ssid=$ssid")
        scope.launch {
            try {
                val device = DeviceState.sample(this@HushListenerService, needsHeadphones, true, needsLocation)
                val appName = NotificationFields.appLabel(this@HushListenerService, packageName)
                val decision = engine.evaluateEvent(event, ssid, activeRules, device, packageName, appName)
                if (decision.matched) {
                    // 事件无源通知，仅执行副作用（通知提醒等），不涉及改写/丢弃通知。
                    sideEffects.execute(decision.sideEffects)
                    recordFires(decision)
                }
            } catch (t: Throwable) {
                Logger.e("wifi event failed", t)
            }
        }
    }

    /** 进入/离开某围栏的那一刻：评估位置事件规则并执行其副作用。 */
    private fun onGeofenceEvent(key: String, entered: Boolean) {
        if (!masterEnabled) return
        Logger.i("geofence event: $key entered=$entered")
        scope.launch {
            try {
                val device = DeviceState.sample(this@HushListenerService, needsHeadphones, true, needsLocation)
                val appName = NotificationFields.appLabel(this@HushListenerService, packageName)
                val decision = engine.evaluateLocationEvent(key, entered, activeRules, device, packageName, appName)
                if (decision.matched) {
                    sideEffects.execute(decision.sideEffects)
                    recordFires(decision)
                }
            } catch (t: Throwable) {
                Logger.e("geofence event failed", t)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = RuleRepository.get(this)
        logRepository = NotificationLogRepository.get(this)
        settings = SettingsStore.get(this)
        channels = ChannelManager(this)
        modifier = NotificationModifier(this, channels)
        sideEffects = SideEffectExecutor(this, scope, tts)
        channels.ensureBaseChannels()
        // 恢复并持久化运行时状态（冷却 / 静音 / 变量），使其跨进程重启依然有效。
        RuntimeStateStore.init(this)
        // 围栏穿越的那一刻 → 触发位置事件规则。
        GeofenceManager.crossingListener = { key, entered -> onGeofenceEvent(key, entered) }
        ContextCompat.registerReceiver(
            this,
            packageChangeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // 规则观察者必须长期存活：任一次更新处理出错都不能让整条 Flow 终止，
        // 否则后续改规则将不再动态生效（需重开开关才行）。逐次 runCatching + 整体 retry 兜底。
        scope.launch {
            repository.observeAll()
                .retry { e -> Logger.e("rules flow error; retrying", e); delay(1000); true }
                .collectLatest { rules ->
                    runCatching {
                        setActiveRules(rules.filter(Rule::enabled))
                        Logger.i("rules loaded: ${activeRules.size} enabled of ${rules.size}")
                    }.onFailure { Logger.e("setActiveRules failed", it) }
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
        scope.launch { settings.danmakuConfig.collectLatest { DanmakuController.updateConfig(it) } }
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
        // 就地静音后由系统放回的原通知：跳过处理（本来就已静默）。见 [inPlaceSilenced]。
        inPlaceSilenced.remove(sbn.key)?.let { deadline ->
            if (SystemClock.elapsedRealtime() < deadline) return
        }

        scope.launch {
            try {
                process(sbn)
            } catch (t: Throwable) {
                Logger.e("process failed for ${sbn.packageName}", t)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        // 当源通知被移除（用户清除或应用自行撤回）时，连带移除我们为其重新发布的副本，
        // 否则改写后的副本会滞留在通知栏。我们自己重发的副本由本应用拥有，跳过它以免误删/递归。
        if (sbn.packageName == packageName) return
        // 重新发布流程本身会调用 cancelNotification(key) 撤销源通知——这会让本方法以
        // REASON_LISTENER_CANCEL 收到同一条源通知的移除回调。此时绝不能连带撤销副本：
        // 副本和「源通知撤销」共用同一个由 sbn.key 派生的通知 id（见 NotificationModifier.
        // notifyId），若照常清理会把刚发布的副本原地撤销掉——静音/改写类通知因此从不显示。
        // 只有真正的外部移除（用户清除 / 源应用自行撤回或更新）才需要连带清理副本。
        if (reason == REASON_LISTENER_CANCEL) return
        if (::modifier.isInitialized) modifier.cancelReposted(sbn)
    }

    private suspend fun process(sbn: StatusBarNotification) {
        val device = DeviceState.sample(this, needsHeadphones, needsWifi, needsLocation)
        val appName = NotificationFields.appLabel(this, sbn.packageName)
        // 应用分身（应用双开）的通知运行在非主用户空间（如 ColorOS user 999），包名与本体相同，
        // 只能靠所属用户区分。通知 key 形如 "userId|pkg|id|tag|uid"，前缀即所属 userId。
        val userId = sbn.key.substringBefore("|").toIntOrNull() ?: 0
        // 常驻通知（VPN / 音乐 / 下载 / 前台服务 / 来电 / 导航等）：综合 flags 与 category 判定，
        // 见 [NotificationFields.isPersistent]。这类通知不写入历史、默认也不触发弹幕。
        val isPersistent = NotificationFields.isPersistent(sbn)
        val originalImportance = originalImportanceOf(sbn)
        val ctx = MatchContext(
            packageName = sbn.packageName,
            appName = appName,
            fields = NotificationFields.extract(sbn),
            isOngoing = sbn.isOngoing,
            hasReply = NotificationFields.hasReplyAction(sbn),
            device = device,
            userId = userId,
            isPersistent = isPersistent,
        )

        val decision = engine.evaluate(ctx, activeRules)

        // 沉浸弹幕：开启且当前处于全屏（横屏看视频/玩游戏）时，把原本仍会原生弹出的通知
        // 改为弹幕呈现——强制丢弃原生通知并注入一条弹幕，交由下方 discard 路径统一处理。
        // 常驻通知不参与（不替换、不弹幕）。
        if (immersiveDanmaku && !isPersistent && !decision.discard &&
            isFullscreen() && DanmakuController.canShow(this)
        ) {
            decision.sideEffects.add(
                SideEffect.Danmaku(TemplateEngine.render("{app}: {title} {text}", ctx))
            )
            decision.discard = true
            decision.matched = true
        }

        if (decision.matched) {
            applyDecision(sbn, decision, appName, originalImportance, isPersistent)
            recordFires(decision)
        }
        // 规则仍会照常对常驻通知求值——这里只跳过记录。
        if (logActivity && !isPersistent) logNotification(sbn, appName, decision, originalImportance)
    }

    /** 源通知自身的系统重要性（来自 Ranking），映射失败/取不到时返回 null。 */
    private fun originalImportanceOf(sbn: StatusBarNotification): Importance? {
        val ranking = android.service.notification.NotificationListenerService.Ranking()
        val found = runCatching { currentRanking?.getRanking(sbn.key, ranking) }.getOrNull() ?: return null
        if (!found) return null
        return when (ranking.importance) {
            android.app.NotificationManager.IMPORTANCE_MIN -> Importance.MIN
            android.app.NotificationManager.IMPORTANCE_LOW -> Importance.LOW
            android.app.NotificationManager.IMPORTANCE_DEFAULT -> Importance.DEFAULT
            android.app.NotificationManager.IMPORTANCE_HIGH,
            android.app.NotificationManager.IMPORTANCE_MAX -> Importance.HIGH
            else -> null
        }
    }

    private suspend fun logNotification(
        sbn: StatusBarNotification,
        appName: String,
        decision: Decision,
        originalImportance: Importance?,
    ) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        // 空白通知（既无标题也无正文）无展示价值，不记入历史。
        if (title.isBlank() && text.isBlank()) return
        val outcome = when {
            decision.discard -> NotificationLog.OUTCOME_DISCARDED
            // 「仅静音」并未改写通知（原通知保留，只是不响不震），与重发副本的「已修改」区分开。
            decision.silenceOnly(originalImportance) -> NotificationLog.OUTCOME_SILENCED
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

    private fun applyDecision(
        sbn: StatusBarNotification,
        decision: Decision,
        appName: String,
        originalImportance: Importance?,
        isPersistent: Boolean,
    ) {
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
            // 「仅静音」（不发声不震动之外无任何改动）：必须保留原通知，绝不重发副本——
            // 副本会脱离原应用，渠道、长按设置、后续更新等原生行为全部丢失。只有本次确实
            // 会发声/震动时才就地静音（短暂 snooze 掐断提醒，系统随后把原通知原样、静默地
            // 放回）；本就静默的通知（渠道重要性低、勿扰拦截、「仅首次提醒」的同 key 更新、
            // 组内提醒收敛）什么也不做，避免通知在栏上无谓地消失又闪回。常驻/ongoing 通知
            // 系统会静默忽略 snooze（掐不断，还会留下永不放回的脏记录），一并跳过。
            // Android 11 以下 snooze 放回时会再次响铃，就地静音不可用——宁可放过这一声，
            // 也不重发副本。
            decision.silenceOnly(originalImportance) -> when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.R ->
                    Logger.i("silence: pre-R, leaving ${sbn.key} untouched")
                isPersistent -> Logger.i("silence: persistent, skip snooze ${sbn.key}")
                wouldAlert(sbn) -> silenceInPlace(sbn)
            }
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
     * 就地静音：短暂 snooze 源通知以掐断正在播放的声音/振动，到期后系统把原通知原样、
     * 静默地放回。先登记 key 再 snooze，确保放回回调到达时一定能被 [inPlaceSilenced] 跳过；
     * snooze 失败则收回登记（不做任何回退——保留原通知优先于掐断这一声）。
     */
    private fun silenceInPlace(sbn: StatusBarNotification) {
        inPlaceSilenced[sbn.key] = SystemClock.elapsedRealtime() + SILENCE_SKIP_WINDOW_MS
        val result = runCatching { snoozeNotification(sbn.key, SILENCE_SNOOZE_MS) }
        if (result.isFailure) {
            inPlaceSilenced.remove(sbn.key)
            Logger.w("silence: snooze failed for ${sbn.key}", result.exceptionOrNull())
        } else {
            Logger.i("silence: snoozed ${sbn.key}")
        }
    }

    /**
     * 判断这次发布是否会真正发声/震动——只有会响的才值得 snooze 掐断。刻意不拿
     * lastAudiblyAlertedMillis 与本次 postTime 比先后来判断“这次响没响”：该时间戳经排名
     * 更新异步送达，读到的多半还是响铃前的快照，会把该掐的误判成不用掐。改用确定性的
     * 信号做排除：
     *  · 渠道重要性低于 DEFAULT → 系统本就不发声不震动；
     *  · 被勿扰模式拦截 → 不发声；
     *  · 带 FLAG_ONLY_ALERT_ONCE 且此前已响过（时间戳早于本次发布，属旧快照的可靠信息）
     *    → 同 key 更新不再响；
     *  · 通知组按 groupAlertBehavior 收敛提醒时，被静默的那一方（child 或 summary）不响。
     * 排除不了的一律视为会响（宁可多 snooze 一次，不能漏掉掐断）；取不到排名时同理。
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun wouldAlert(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        val ranking = android.service.notification.NotificationListenerService.Ranking()
        val found = runCatching { currentRanking?.getRanking(sbn.key, ranking) }.getOrNull()
        if (found == true) {
            if (ranking.importance < android.app.NotificationManager.IMPORTANCE_DEFAULT) {
                return silentBecause("importance=${ranking.importance}", sbn)
            }
            if (!ranking.matchesInterruptionFilter()) return silentBecause("dnd", sbn)
            val alertedBefore = ranking.lastAudiblyAlertedMillis in 1 until sbn.postTime
            if (n.flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE != 0 && alertedBefore) {
                return silentBecause("alert-once update", sbn)
            }
        }
        if (sbn.isGroup) {
            val isSummary = n.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0
            when (n.groupAlertBehavior) {
                android.app.Notification.GROUP_ALERT_SUMMARY ->
                    if (!isSummary) return silentBecause("group alerts via summary", sbn)
                android.app.Notification.GROUP_ALERT_CHILDREN ->
                    if (isSummary) return silentBecause("group alerts via children", sbn)
            }
        }
        return true
    }

    private fun silentBecause(reason: String, sbn: StatusBarNotification): Boolean {
        Logger.i("silence: $reason, no alert expected, skip snooze ${sbn.key}")
        return false
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
        GeofenceManager.crossingListener = null
        wifiCallback?.let { cb ->
            runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
        }
        wifiCallback = null
        runCatching { unregisterReceiver(packageChangeReceiver) }
        if (tts.isInitialized()) tts.value.shutdown()
        scope.cancel()
    }

    companion object {

        /** 就地静音的 snooze 时长：足以让系统掐断声音/振动，又短到通知栏几乎无感。 */
        private const val SILENCE_SNOOZE_MS = 1_000L

        /** 放回跳过窗口：放回一般在 1 秒后到达，Doze 等延迟场景放宽到 30 秒兜底。 */
        private const val SILENCE_SKIP_WINDOW_MS = 30_000L

        /** 当监听器处于连接状态时非空；供 UI 用于显示状态。 */
        @Volatile
        var instance: HushListenerService? = null
            private set

        fun isConnected(): Boolean = instance != null

        /**
         * 当已授权但监听器未连接时，强制系统重新绑定（UI 打开时 / 看门狗唤醒时调用以自动恢复）。
         *
         * ColorOS/MIUI 等被省电策略杀掉监听后，单纯 [NotificationListenerService.requestRebind]
         * 往往无效——必须像用户手动「重开一次通知使用权」那样触发系统重绑。这里用「禁用→启用
         * 组件」达到同样效果，但无需用户操作：通知使用权的授权不受影响，仅迫使系统重新绑定服务。
         */
        fun requestRebindIfNeeded(context: android.content.Context) {
            if (isConnected()) return
            if (!NotificationAccess.isGranted(context)) return
            val component = android.content.ComponentName(context, HushListenerService::class.java)
            runCatching { NotificationListenerService.requestRebind(component) }
            runCatching {
                val pm = context.packageManager
                pm.setComponentEnabledSetting(
                    component,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP,
                )
                pm.setComponentEnabledSetting(
                    component,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP,
                )
                // 切换组件后再请求一次绑定。
                NotificationListenerService.requestRebind(component)
            }.onFailure { Logger.w("force rebind failed: ${it.message}") }
        }
    }
}
