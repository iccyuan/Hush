package com.iccyuan.hush.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat
import com.iccyuan.hush.data.model.Condition
import com.iccyuan.hush.data.model.Rule
import com.iccyuan.hush.engine.DeviceContext
import com.iccyuan.hush.engine.RuleEngine
import com.iccyuan.hush.engine.VariableStore
import com.iccyuan.hush.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 「静音应用」条件翻转的主动监听：设置静音的规则条件一不成立（如过了静音时段），立刻把被
 * 改哑的渠道还原；一恢复成立，立刻重新改哑。若只靠通知到达时的机会性修正
 * （[HushListenerService.process]，保留为兜底），翻转后的**第一条**通知提醒行为总是错的——
 * 时段刚结束时它仍然无声。
 *
 * ## 资源开销
 *
 * 一切监听都按需存在，按条件类型只挂真正用到的那几种；没有任何「带条件的静音」在生效时
 * 全部撤下，零常驻开销（无条件静音永远生效，不存在翻转，无需监听）：
 *
 *  · **时间 / 节假日 / 冷却**：不用 AlarmManager（免精确闹钟权限，绝不主动唤醒设备）——
 *    一个挂起协程 delay 到下一个翻转边界，挂起协程本身不占任何系统资源。深度休眠可能让它
 *    晚醒，无妨：真有通知到达时兜底路径会就地修正，而没有通知到达时渠道晚还原也无人感知。
 *  · **充电 / 亮屏 / 电量**：一个动态广播接收器，filter 只含用到的 action。电量广播每变
 *    1% 就来一次，只有跨过某条规则设定的阈值才可能改变条件真值，其余就地忽略、不重算。
 *  · **Wi-Fi**：一个 NetworkCallback（仅连/断时回调）。**耳机**：一个 AudioDeviceCallback，
 *    只对耳机类设备的插拔作出反应。
 *  · **位置**：不新建监听——围栏穿越事件本就流经服务（GeofenceManager.crossingListener），
 *    由服务在那里触发一次重算。
 *
 * 重算信号是合流的（CONFLATED）：多个事件挤在一起只跑一遍；每遍 = 采样一次设备状态 +
 * 对每个被静音应用求值一次条件，毫秒级。渠道的改哑/还原本身幂等（已哑不再写、无快照直接
 * 返回），重复重算不会触碰系统。
 */
class MuteConditionMonitor(
    private val listener: NotificationListenerService,
    private val scope: CoroutineScope,
    private val activeRules: () -> List<Rule>,
) {
    private val log = Logger.scoped("mute-watch")
    private val engine = RuleEngine()

    // 被静音应用所属的用户空间（本体 0 / 分身如 999），由服务在静音落实时登记：
    // VariableStore 的静音记录不含 userId，而主动改哑需要它定位目标。进程重启后为空，
    // 此时先按主用户处理，分身应用的下一条通知会经机会性路径补齐。
    private val knownUserIds = ConcurrentHashMap<String, Int>()

    // 合流的重算信号：翻转事件可能连珠而至（亮屏 + 充电同时来），挤在一起只跑一遍。
    private val signals = Channel<String>(Channel.CONFLATED)

    private var timeFlipJob: Job? = null
    private var receiver: BroadcastReceiver? = null
    private var receiverActions: Set<String> = emptySet()
    private var wifiCallback: ConnectivityManager.NetworkCallback? = null
    private var audioCallback: AudioDeviceCallback? = null
    @Volatile private var batteryThresholds: List<Int> = emptyList()
    @Volatile private var lastBatteryPct = -1

    init {
        scope.launch {
            for (reason in signals) {
                runCatching { resyncNow(reason) }
                    .onFailure { log.e("resync failed ($reason)", it) }
            }
        }
    }

    /** 登记某个被静音应用所属的用户空间，供主动改哑定位目标。 */
    fun noteUserId(pkg: String, userId: Int) {
        knownUserIds[pkg] = userId
    }

    /** 请求一次重算（合流；任意线程调用都安全）。 */
    fun resync(reason: String) {
        signals.trySend(reason)
    }

    /** 服务销毁时撤下全部监听。信号消费协程随 [scope] 一同取消，无需单独处理。 */
    fun shutdown() = teardownWatchers()

    private fun resyncNow(reason: String) {
        val rules = activeRules()
        // 静音记录 -> 拥有它的启用规则。规则已删/停用的记录不在这里解除（那是引擎短路与
        // 规则页各自的职责），只是不为它挂监听。
        val owned = VariableStore.mutesSnapshot().mapNotNull { (pkg, ruleId) ->
            rules.firstOrNull { it.id == ruleId }?.let { pkg to it }
        }
        val conds = owned.flatMap { (_, rule) -> rule.conditions }
        if (conds.isEmpty()) {
            teardownWatchers()
            return
        }
        val device = DeviceState.sample(
            listener,
            sampleHeadphones = conds.any { it is Condition.HeadphonesCondition },
            sampleWifi = conds.any { it is Condition.WifiCondition },
            sampleLocation = conds.any { it is Condition.LocationCondition },
        )
        lastBatteryPct = device.batteryPercent
        for ((pkg, rule) in owned) {
            // 无条件静音永远生效，不存在翻转；其渠道改哑由命中路径维护。
            if (rule.conditions.isEmpty()) continue
            val active = engine.conditionsActiveNow(rule, device)
            log.d("resync($reason): $pkg -> ${if (active) "active" else "suspended"}")
            runCatching {
                if (active) ChannelSilencer.ensureSilenced(listener, pkg, knownUserIds[pkg] ?: 0)
                else ChannelSilencer.restore(listener, pkg)
            }.onFailure { log.e("apply failed for $pkg", it) }
        }
        updateWatchers(owned, conds, device)
    }

    private fun updateWatchers(owned: List<Pair<String, Rule>>, conds: List<Condition>, device: DeviceContext) {
        scheduleTimeFlip(owned, conds, device)
        setBroadcastWatcher(
            charging = conds.any { it is Condition.ChargingCondition },
            screen = conds.any { it is Condition.ScreenCondition },
            batteryPercents = conds.filterIsInstance<Condition.BatteryLevelCondition>().map { it.percent },
        )
        setWifiWatcher(conds.any { it is Condition.WifiCondition })
        setHeadphonesWatcher(conds.any { it is Condition.HeadphonesCondition })
    }

    private fun teardownWatchers() {
        timeFlipJob?.cancel()
        timeFlipJob = null
        setBroadcastWatcher(charging = false, screen = false, batteryPercents = emptyList())
        setWifiWatcher(false)
        setHeadphonesWatcher(false)
    }

    /**
     * 时间类条件的下一个翻转时刻：所有时间窗口的起止分钟、节假日的午夜、冷却的到期时刻中
     * 最近的那个。到点重算一次即可——翻转时刻真值未必真的变化（如周几不匹配），这样的
     * 无谓唤醒每天至多几次，成本可忽略，不值得为省它去精算日历。
     */
    private fun scheduleTimeFlip(owned: List<Pair<String, Rule>>, conds: List<Condition>, device: DeviceContext) {
        timeFlipJob?.cancel()
        timeFlipJob = null
        val delays = mutableListOf<Long>()
        // 距「下一个第 minute 分钟」的毫秒数。现实时区都按整分对齐，epoch 毫秒对 60_000
        // 取余即当前分钟内已流逝的毫秒。
        fun untilMinute(minute: Int): Long {
            var deltaMin = minute - device.minuteOfDay
            if (deltaMin <= 0) deltaMin += 24 * 60
            return deltaMin * 60_000L - device.nowMillis % 60_000
        }
        for (c in conds) when (c) {
            is Condition.TimeCondition -> {
                delays += untilMinute(c.startMinute)
                delays += untilMinute(c.endMinute)
            }
            // 「工作日/节假日」在午夜翻转。
            is Condition.HolidayCondition -> delays += untilMinute(24 * 60)
            else -> {}
        }
        for ((_, rule) in owned) {
            if (rule.conditions.none { it is Condition.CooldownCondition }) continue
            VariableStore.cooldownsSnapshot()[rule.id]?.let { until ->
                if (until > device.nowMillis) delays += until - device.nowMillis
            }
        }
        val next = delays.minOrNull() ?: return
        timeFlipJob = scope.launch {
            // 加一点余量，醒来时确保已越过边界。delay 基于单调时钟，深度休眠可能晚醒（见类注释）。
            delay((next + FLIP_SLACK_MS).coerceAtLeast(MIN_FLIP_DELAY_MS))
            resync("time-flip")
        }
    }

    private fun setBroadcastWatcher(charging: Boolean, screen: Boolean, batteryPercents: List<Int>) {
        batteryThresholds = batteryPercents
        val actions = buildSet {
            if (charging) {
                add(Intent.ACTION_POWER_CONNECTED)
                add(Intent.ACTION_POWER_DISCONNECTED)
            }
            if (screen) {
                add(Intent.ACTION_SCREEN_ON)
                add(Intent.ACTION_SCREEN_OFF)
            }
            if (batteryPercents.isNotEmpty()) add(Intent.ACTION_BATTERY_CHANGED)
        }
        if (actions == receiverActions) return
        receiver?.let { runCatching { listener.unregisterReceiver(it) } }
        receiver = null
        receiverActions = actions
        if (actions.isEmpty()) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action ?: return
                if (action == Intent.ACTION_BATTERY_CHANGED && !crossedBatteryThreshold(intent)) return
                resync(action.substringAfterLast('.'))
            }
        }
        ContextCompat.registerReceiver(
            listener, r,
            IntentFilter().apply { actions.forEach(::addAction) },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiver = r
    }

    /** 电量广播每变 1% 一次；只有跨过某条规则设定的阈值，条件真值才可能翻转。 */
    private fun crossedBatteryThreshold(intent: Intent): Boolean {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return false
        val pct = level * 100 / scale
        val prev = lastBatteryPct
        lastBatteryPct = pct
        if (prev < 0) return true
        return batteryThresholds.any { t -> (prev < t) != (pct < t) || (prev > t) != (pct > t) }
    }

    private fun setWifiWatcher(needed: Boolean) {
        val cm = listener.getSystemService(ConnectivityManager::class.java) ?: return
        if (needed && wifiCallback == null) {
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = resync("wifi-up")
                override fun onLost(network: Network) = resync("wifi-down")
            }
            val req = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            runCatching { cm.registerNetworkCallback(req, cb); wifiCallback = cb }
                .onFailure { log.w("wifi watcher register failed: ${it.message}") }
        } else if (!needed && wifiCallback != null) {
            runCatching { cm.unregisterNetworkCallback(wifiCallback!!) }
            wifiCallback = null
        }
    }

    private fun setHeadphonesWatcher(needed: Boolean) {
        val am = listener.getSystemService(AudioManager::class.java) ?: return
        if (needed && audioCallback == null) {
            val cb = object : AudioDeviceCallback() {
                // 注册瞬间会先回调一次现有设备列表，触发一次重算；重算幂等，无妨。
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                    if (addedDevices.any(DeviceState::isHeadphone)) resync("headphones-added")
                }
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                    if (removedDevices.any(DeviceState::isHeadphone)) resync("headphones-removed")
                }
            }
            am.registerAudioDeviceCallback(cb, null)
            audioCallback = cb
        } else if (!needed && audioCallback != null) {
            am.unregisterAudioDeviceCallback(audioCallback)
            audioCallback = null
        }
    }

    private companion object {
        /** 翻转边界后的余量：醒来时确保已越过边界，避免贴着边界重算判错。 */
        const val FLIP_SLACK_MS = 2_000L

        /** 最短等待，防御计算出的极小值造成空转。 */
        const val MIN_FLIP_DELAY_MS = 1_000L
    }
}
