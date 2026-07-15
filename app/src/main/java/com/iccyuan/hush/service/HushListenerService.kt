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
import com.iccyuan.hush.data.InstalledApps
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
import com.iccyuan.hush.data.db.BuzzJson
import com.iccyuan.hush.engine.Decision
import com.iccyuan.hush.engine.MatchContext
import com.iccyuan.hush.engine.MatchTrace
import com.iccyuan.hush.engine.NearMiss
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
import kotlinx.serialization.builtins.ListSerializer
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

    // 静音是这里最费解、最需要现场排查的一块（放回二次响铃、渠道改写、节流），单独一个前缀，
    // logcat 里 `grep silence` 就能把它整条链路拎出来。
    private val silenceLog = Logger.scoped("silence")

    private lateinit var repository: RuleRepository
    private lateinit var logRepository: NotificationLogRepository
    private lateinit var settings: SettingsStore
    private lateinit var channels: ChannelManager
    private lateinit var modifier: NotificationModifier
    // 惰性构造：仅当某条动作真正朗读文本时才绑定 TextToSpeech 引擎，避免大多数从不使用
    // 「朗读」功能的用户在服务启动时白白付出这份开销。
    private val tts = lazy { TtsManager(this) }
    private lateinit var sideEffects: SideEffectExecutor
    // 「静音应用」条件翻转的主动监听：条件不成立立刻还原渠道、恢复成立立刻改哑，
    // 不必等下一条通知到达。监听按需挂载，无带条件的静音时零常驻开销。
    private lateinit var muteMonitor: MuteConditionMonitor

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

    // 「就地静音」short-snooze 后系统会把原通知放回并再次回调 onNotificationPosted。登记这些
    // 通知，以便跳过放回的那一次——否则放回的通知会再次命中规则 → 再次 snooze，循环不止，
    // 且副作用（toast/webhook 等）会重复执行。
    //
    // 必须连 postTime 一起比对，只认「原样放回」的那一条：光凭 key 相同是不够的——聊天类应用
    // 正是用同一个 key 反复更新同一条通知来展示新消息的，那些更新会被误当成放回而整个跳过，
    // 表现为「连续消息在通知记录里丢失」，且新消息根本不走规则。放回是系统把原通知原样放回，
    // postTime 不变；应用的新更新则会带上新的 postTime。
    private data class SilenceMark(val postTime: Long, val expiresAt: Long)
    private val inPlaceSilenced = ConcurrentHashMap<String, SilenceMark>()

    // 常驻通知的求值节流（VPN / 播放器 / 下载会秒级刷新同一条通知）。见 [EvalThrottle]。
    private val persistentThrottle = EvalThrottle(PERSISTENT_EVAL_INTERVAL_MS)

    // 每次就地静音的 snooze 时刻（墙钟），供放回后判定「这台机器放回时是否又响了一次」。
    // 见 [verifySilentPutback]——基准必须是 snooze 时刻而非放回时刻。
    private val snoozedAtByKey = ConcurrentHashMap<String, Long>()


    // 应用安装/卸载/更新时，失效对应包名的应用标签缓存（见 [NotificationFields.appLabel]），
    // 并清掉应用选择器的整表缓存——否则新装的应用要等本进程重启才会出现在选择列表里。
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.data?.schemeSpecificPart?.let { NotificationFields.invalidateAppLabel(it) }
            InstalledApps.invalidate()
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
        // 规则变化可能改动静音规则的条件，或删掉/新增带条件的静音——重算一次监听与渠道状态。
        if (::muteMonitor.isInitialized) muteMonitor.resync("rules-changed")
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
        // 位置条件的静音复用这条事件流，无需自建监听（见 MuteConditionMonitor）。
        if (::muteMonitor.isInitialized) muteMonitor.resync("geofence")
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
        muteMonitor = MuteConditionMonitor(this, scope) { activeRules }
        // 静音解除的入口分散在 UI 与引擎多处，统一在这里挂钩，确保被改哑的渠道一定会被还原。
        VariableStore.setUnmuteListener { pkg ->
            scope.launch {
                runCatching { ChannelSilencer.restore(this@HushListenerService, pkg) }
                    .onFailure { Logger.e("channel-restore failed for $pkg", it) }
                // 静音少了一个，对应的条件监听可能就不再需要了。
                muteMonitor.resync("unmuted")
            }
        }
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
        if (!masterEnabled) return
        // 切勿处理我们自己重新发布的副本——否则会陷入无限循环。
        if (sbn.packageName == packageName) return
        // 就地静音后由系统原样放回的那一条：跳过处理（它本来就已静默）。只认 postTime 未变的，
        // 否则会把应用「用同一个 key 更新出的新消息」也一并吞掉。见 [inPlaceSilenced]。
        inPlaceSilenced.remove(sbn.key)?.let { mark ->
            val fresh = SystemClock.elapsedRealtime() < mark.expiresAt
            if (fresh && sbn.postTime == mark.postTime) {
                verifySilentPutback(sbn.key)
                return
            }
            if (fresh) {
                silenceLog.i("same key but postTime moved (${mark.postTime} → ${sbn.postTime}); " +
                    "treating as a new update, not the putback")
            }
        }

        // 常驻通知（VPN / 音乐 / 下载 / 前台服务）会秒级重发同一条通知来刷新流量、进度这类数字
        // ——实测某 VPN 每秒一次，一天八万多次。它们本就不进历史，可每一次仍会完整跑一遍规则：
        // 采样设备状态、抽取字段、遍历全部规则，纯属白烧电。这类通知表示的是**持续状态**而非
        // 事件，隔一会儿重算一次足矣，语义上没有损失（针对它们的规则最多晚 [PERSISTENT_EVAL_INTERVAL_MS]
        // 生效，而它们本来就一直在那儿）。
        if (NotificationFields.isPersistent(sbn) &&
            !persistentThrottle.due(sbn.key, SystemClock.elapsedRealtime())
        ) return

        // 每条通知都会走到这里——用 d()，正式版里不输出：没人看，还平白耗电、把 logcat 冲爆。
        // 位置也刻意放在节流之后，否则每秒刷新的常驻通知光靠这一行就能把日志挤满。
        Logger.d("posted from ${sbn.packageName}; rules=${activeRules.size}")

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
        // 通知没了，它的节流记录也就没有意义了——VPN 重连后的第一条应当立刻被求值。
        persistentThrottle.forget(sbn.key)
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

        // 「静音应用」处于暂停期（应用在静音名单里，但设置静音的规则条件此刻不成立，如已过
        // 静音时段）：引擎只是不再输出静音决定，而渠道级静音是系统层的持久改写——不主动还原
        // 的话，暂停期的通知照样无声无振动，表现为「非命中时段也被静音」。条件翻转通常已由
        // [MuteConditionMonitor] 在翻转瞬间处理，这里是它晚醒/漏事件时的兜底；还原幂等
        // （没有快照时直接返回），回到时段内命中路径的 ensureSilenced 会重新改哑。
        if (decision.appMuteActive == false) {
            runCatching { ChannelSilencer.restore(this, sbn.packageName) }
                .onFailure { silenceLog.e("channel-restore(suspended) failed for ${sbn.packageName}", it) }
        }

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
                    traces = if (decision.traces.isEmpty()) "" else {
                        runCatching { BuzzJson.encodeToString(TRACES, decision.traces.toList()) }
                            .getOrDefault("")
                    },
                    nearMisses = nearMissesJson(decision),
                )
            )
        }.onFailure {
            // 别把它咽下去：写不进历史，用户看到的是「通知记录莫名少了几条」，而这里连一行线索
            // 都不留，只能靠猜。历史写失败不该拖垮通知处理，但必须留下痕迹。
            Logger.e("history insert failed for ${sbn.packageName}", it)
        }
    }

    /**
     * 「差一点就命中」只对**未被处理**的通知有意义——命中了的通知，用户要问的是「为什么命中」
     * （traces 已经答了），而不是「还有谁差一点」。
     *
     * 只留走得最远的几条：规则一多，「差一点」的名单会很长，而卡在条件关的（触发器都过了、
     * 只差时机）显然比卡在触发器关的更接近，也更是用户想看的。
     */
    private fun nearMissesJson(decision: Decision): String {
        if (decision.matched || decision.nearMisses.isEmpty()) return ""
        val closest = decision.nearMisses
            .sortedByDescending { it.blockedAt.ordinal }
            .take(MAX_NEAR_MISSES)
        return runCatching { BuzzJson.encodeToString(NEAR_MISSES, closest) }.getOrDefault("")
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
        // 「静音应用」：优先把目标应用自己的渠道改成不发声不振动——系统层面直接静音，通知原样保留。
        // 这是唯一能同时做到「完全静音」与「不动原通知」的途径（就地静音依赖 snooze 放回静默，
        // 部分 ROM 会在放回时重放完整提醒；重发副本又会让通知脱离原应用）。需要配套设备关联，
        // 未关联时静默降级为就地静音。
        //
        // 两条路径都要覆盖：本次刚触发静音（MuteApp 副作用），以及该应用早已在静音名单里
        // 且静音**此刻生效**（引擎在 evaluate 顶部短路，不再产生副作用）——后者用幂等的
        // ensureSilenced 补齐，以防渠道改写因进程重启、升级、当时尚未关联配套设备而没落实。
        // 判据必须是 appMuteActive 而非「在静音名单里」：静音暂停期间（条件不成立）该应用的
        // 通知若命中了其他规则，绝不能借机把刚还原的渠道又改哑回去。
        val muting = decision.sideEffects.filterIsInstance<SideEffect.MuteApp>().firstOrNull()
        val userId = sbn.key.substringBefore("|").toIntOrNull() ?: 0
        when {
            muting != null -> {
                muteMonitor.noteUserId(muting.pkg, muting.userId)
                scope.launch {
                    runCatching { ChannelSilencer.silence(this@HushListenerService, muting.pkg, muting.userId) }
                        .onFailure { Logger.e("channel-silence failed for ${muting.pkg}", it) }
                }
            }
            decision.appMuteActive == true -> {
                muteMonitor.noteUserId(sbn.packageName, userId)
                // 带上本条通知的渠道 id：静音后应用新建的渠道（聊天应用常为新会话建渠道）
                // 不在已改哑名单里，ensureSilenced 据此识别并补一次改哑。
                val channelId = sbn.notification.channelId
                scope.launch {
                    runCatching {
                        ChannelSilencer.ensureSilenced(this@HushListenerService, sbn.packageName, userId, channelId)
                    }.onFailure { Logger.e("channel-silence(ensure) failed for ${sbn.packageName}", it) }
                }
            }
        }
        sideEffects.execute(decision.sideEffects)
        // 静音名单刚多了一员（VariableStore.muteApp 已在上面同步执行）：让监听器按这条
        // 静音规则的条件挂上需要的翻转监听。
        if (muting != null) muteMonitor.resync("mute-added")

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
                // 本条通知所在渠道已被改哑：系统压根不会为它发声/振动，无需再 snooze 掐断——
                // 通知原地不动，既不会从通知栏消失再放回，也就不存在「放回时二次响铃」的问题。
                // 静音后新建的渠道不在此列：那一声要靠下面的 snooze 掐断，渠道随后被补齐改哑。
                ChannelSilencer.isSilenced(sbn.packageName, sbn.notification.channelId) ->
                    silenceLog.i("channel already silenced, nothing to do for ${sbn.key}")
                Build.VERSION.SDK_INT < Build.VERSION_CODES.R ->
                    silenceLog.i("pre-R, leaving ${sbn.key} untouched")
                isPersistent -> silenceLog.i("persistent, skip snooze ${sbn.key}")
                wouldAlert(sbn) && shouldSnooze() -> silenceInPlace(sbn)
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
     * 就地静音：短暂 snooze 源通知以掐断正在播放的声音/振动，到期后系统把原通知原样放回。
     * 先登记 key 再 snooze，确保放回回调到达时一定能被 [inPlaceSilenced] 跳过；snooze 失败
     * 则收回登记（不做任何回退——保留原通知优先于掐断这一声）。
     *
     * 这条路只是渠道级静音（[ChannelSilencer]）不可用时的兜底，且在放回会二次响铃的机型上
     * 根本不该走——见 [shouldSnooze]。
     */
    private fun silenceInPlace(sbn: StatusBarNotification) {
        inPlaceSilenced[sbn.key] = SilenceMark(
            postTime = sbn.postTime,
            expiresAt = SystemClock.elapsedRealtime() + SILENCE_SKIP_WINDOW_MS,
        )
        val snoozedAt = System.currentTimeMillis()
        val result = runCatching { snoozeNotification(sbn.key, SILENCE_SNOOZE_MS) }
        if (result.isFailure) {
            inPlaceSilenced.remove(sbn.key)
            silenceLog.w("snooze failed for ${sbn.key}", result.exceptionOrNull())
        } else {
            snoozedAtByKey[sbn.key] = snoozedAt
            silenceLog.i("snoozed ${sbn.key}")
        }
    }

    /**
     * 这台机器上，snooze 掐断还值不值得做。
     *
     * 部分 OEM（实测 ColorOS 16 / Android 16）会在放回时**重放完整提醒**（声音 + 振动），
     * 于是 snooze 非但没静音，反而把提醒推迟一秒再完整响一次——比什么都不做还怪。确认是这种
     * 机型后就不再 snooze：诚实地响一次，并让用户去开「系统级静音」（渠道级静音不受此影响）。
     */
    private fun shouldSnooze(): Boolean {
        if (!RuntimeStateStore.oemRealertsOnPutback) return true
        silenceLog.w("this ROM re-alerts on putback; skipping snooze — enable channel-level mute")
        return false
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
        silenceLog.i("$reason, no alert expected, skip snooze ${sbn.key}")
        return false
    }

    /**
     * 就地静音的放回到达后，复查系统的「最近发声」时间戳，判定这台机器是否在放回时**又响了一次**。
     *
     * 基准必须是 **snooze 时刻**，不能是放回回调到达的时刻：系统先播放提示音、写下时间戳，
     * 之后才回调 onNotificationPosted，因此二次响铃的时间戳总是比放回回调早几毫秒——拿放回
     * 时刻作基准会把它当成"响在放回之前"，永远判定为静默（曾经如此，实测漏判）。而 snooze
     * 早于放回约一秒，任何晚于它的发声都只可能来自放回。
     *
     * 一旦确认，持久化到 [RuntimeStateStore.oemRealertsOnPutback]：此后不再走 snooze 掐断
     * （它在这类机型上只会把提醒推迟一秒再完整响一次，见 [shouldSnooze]），静音改由渠道级
     * 方案承担——那条路不经过 snooze，不受此 ROM 行为影响。
     */
    private fun verifySilentPutback(key: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val snoozedAt = snoozedAtByKey.remove(key) ?: return
        if (RuntimeStateStore.oemRealertsOnPutback) return // 已确认，无需再验
        scope.launch {
            delay(PUTBACK_CHECK_DELAY_MS)
            val ranking = android.service.notification.NotificationListenerService.Ranking()
            val found = runCatching { currentRanking?.getRanking(key, ranking) }.getOrNull() ?: return@launch
            if (!found) return@launch
            val alerted = ranking.lastAudiblyAlertedMillis
            if (alerted > snoozedAt) {
                Logger.w(
                    "silence: putback RE-ALERTED for $key (alerted=$alerted > snoozedAt=$snoozedAt); " +
                        "this ROM breaks silent putback — will mute the notification stream from now on"
                )
                RuntimeStateStore.setOemRealertsOnPutback(true)
            } else {
                silenceLog.i("putback stayed silent for $key (lastAudibly=$alerted, snoozedAt=$snoozedAt)")
            }
        }
    }

    /**
     * 全屏检测（沉浸弹幕用）：屏幕点亮 + 横屏 + **状态栏已隐藏**。
     *
     * 只看横屏是不够的——平板、折叠屏展开态本来就常驻横屏（实测一台 3168×1440 的横屏设备：
     * 桌面、聊天、任何场景都被判成"在看视频"，于是**所有通知都被弹幕丢弃**，表现为通知凭空消失）。
     * 真正的沉浸全屏必然隐藏状态栏，因此以它作为最后一道判据；取不到 insets 时保守地认为
     * 状态栏可见（即不当作全屏，宁可不弹弹幕，也不能把通知吞掉）。
     */
    private fun isFullscreen(): Boolean {
        val pm = getSystemService(android.os.PowerManager::class.java)
        if (pm?.isInteractive != true) return false
        val landscape = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (!landscape) return false
        return !statusBarVisible()
    }

    /** 状态栏当前是否可见。读不到时返回 true（保守：不视为全屏）。 */
    private fun statusBarVisible(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@runCatching true
        val wm = getSystemService(android.view.WindowManager::class.java) ?: return@runCatching true
        wm.currentWindowMetrics.windowInsets
            .isVisible(android.view.WindowInsets.Type.statusBars())
    }.getOrDefault(true)

    private fun recordFires(decision: Decision) {
        if (decision.firedRuleIds.isEmpty()) return
        scope.launch {
            decision.firedRuleIds.forEach { repository.incrementFireCount(it) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        GeofenceManager.crossingListener = null
        if (::muteMonitor.isInitialized) muteMonitor.shutdown()
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

        /**
         * 常驻通知的最小重算间隔。它们表示持续状态（VPN 在连、歌在放、文件在下），刷新的只是
         * 里面的数字，秒级重跑规则毫无意义；针对它们的规则最多晚这么久生效，而它们本就一直在那儿。
         */
        private const val PERSISTENT_EVAL_INTERVAL_MS = 30_000L

        /** 通知历史里「命中取证」的序列化器（见 [NotificationLog.traces]）。 */
        private val TRACES = ListSerializer(MatchTrace.serializer())

        /** 「差一点就命中」的序列化器（见 [NotificationLog.nearMisses]）。 */
        private val NEAR_MISSES = ListSerializer(NearMiss.serializer())

        /** 历史里最多展示几条「差一点」：再多就成了噪音，用户看的是最接近的那一两条。 */
        private const val MAX_NEAR_MISSES = 3

        /** 放回后隔多久复查「是否二次响铃」：要等系统把发声时间戳更新进排名。 */
        private const val PUTBACK_CHECK_DELAY_MS = 3_000L

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
