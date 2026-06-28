package com.iccyuan.hush.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.amap.api.fence.GeoFence
import com.amap.api.fence.GeoFenceClient
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.DPoint
import com.iccyuan.hush.data.HolidayProvider
import com.iccyuan.hush.data.model.Condition
import com.iccyuan.hush.data.model.ConditionLogic
import com.iccyuan.hush.data.model.DayType
import com.iccyuan.hush.data.model.Rule
import com.iccyuan.hush.util.Logger
import java.util.Calendar

/**
 * 用高德 [GeoFenceClient] 注册/同步规则用到的地理围栏。进出由系统级围栏监控（不轮询、省电），
 * 通过广播回调把"在/不在"写入 [GeofenceState]，规则条件再从中读取——不依赖 Google Play 服务。
 *
 * 按需省电：只为「此刻其时间/节假日条件成立」的规则注册围栏。例如规则是「9:00 且 在公司」，
 * 不到 9:00 就不会注册围栏、也不会触发任何定位；到窗口边界时由 [AlarmManager] 唤醒重新评估。
 */
object GeofenceManager {

    private const val ACTION_FENCE = "com.iccyuan.hush.action.GEOFENCE"
    private const val ACTION_RESYNC = "com.iccyuan.hush.action.GEOFENCE_RESYNC"

    private var client: GeoFenceClient? = null
    private var receiver: BroadcastReceiver? = null
    private var registeredKeys: Set<String> = emptySet()
    private var lastRules: List<Rule> = emptyList()

    /** 缓存最新规则并按当前时间重新评估需要注册的围栏。 */
    @Synchronized
    fun sync(context: Context, rules: List<Rule>) {
        lastRules = rules
        reevaluate(context.applicationContext)
    }

    /** 由窗口边界闹钟唤醒：用缓存的规则按「现在」重新评估围栏。 */
    @Synchronized
    private fun resync(app: Context) = reevaluate(app)

    private fun reevaluate(app: Context) {
        // 任意规则用到的位置条件（坐标有效）。
        val located = lastRules.filter { rule ->
            rule.conditions.any { it is Condition.LocationCondition && (it.latitude != 0.0 || it.longitude != 0.0) }
        }
        if (located.isEmpty()) {
            teardown(app)
            registeredKeys = emptySet()
            GeofenceState.reset()
            return
        }

        // 仅保留「此刻时间/节假日条件成立」的规则的围栏——其余规则现在不可能命中，无需耗电定位。
        val now = TimeCtx.now()
        val eligible = located
            .filter { ruleTimeEligible(it, now) }
            .flatMap { it.conditions.filterIsInstance<Condition.LocationCondition>() }
            .filter { it.latitude != 0.0 || it.longitude != 0.0 }
            .distinctBy { it.fenceKey() }
        val keys = eligible.map { it.fenceKey() }.toSet()

        val c = ensureSetup(app)
        if (c != null && keys != registeredKeys) {
            registeredKeys = keys
            runCatching {
                c.removeGeoFence()
                eligible.forEach { f ->
                    c.addGeoFence(DPoint(f.latitude, f.longitude), f.radiusMeters.toFloat(), f.fenceKey())
                }
                // 不再监控的围栏，清掉其「在内」缓存，避免残留误判。
                GeofenceState.retainOnly(keys)
            }.onFailure { Logger.w("geofence sync failed: ${it.message}") }
        }

        scheduleNextBoundary(app, located, now)
    }

    /**
     * 规则此刻是否「值得」注册围栏（省电门控）。按 [ConditionLogic] 判断：
     * - ANY：位置单独就能命中，必须始终监控。
     * - ALL：所有时间段与节假日都需成立，任一不满足即可跳过。
     * - SMART：位置与「时间组」为「与」；时间组成立 =（任一时间段 或 任一节假日）成立，否则跳过。
     */
    private fun ruleTimeEligible(rule: Rule, now: TimeCtx): Boolean {
        val times = rule.conditions.filterIsInstance<Condition.TimeCondition>()
        val holidays = rule.conditions.filterIsInstance<Condition.HolidayCondition>()
        return when (rule.conditionLogic) {
            ConditionLogic.ANY -> true
            ConditionLogic.ALL ->
                (times.isEmpty() || times.all { inTimeWindow(it, now) }) &&
                    (holidays.isEmpty() || holidays.all { it.dayTypes.contains(now.dayType) })
            ConditionLogic.SMART ->
                if (times.isEmpty() && holidays.isEmpty()) true
                else times.any { inTimeWindow(it, now) } || holidays.any { it.dayTypes.contains(now.dayType) }
        }
    }

    private fun inTimeWindow(c: Condition.TimeCondition, now: TimeCtx): Boolean {
        val minute = now.minuteOfDay
        val day = now.isoDay
        return if (c.startMinute <= c.endMinute) {
            minute in c.startMinute until c.endMinute && c.days.contains(day)
        } else {
            if (minute >= c.startMinute) c.days.contains(day)
            else minute < c.endMinute && c.days.contains(if (day == 1) 7 else day - 1)
        }
    }

    /** 在下一个可能改变「资格」的时刻（最近的窗口起止边界，或次日零点）唤醒重新评估。 */
    private fun scheduleNextBoundary(app: Context, located: List<Rule>, now: TimeCtx) {
        val am = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = resyncIntent(app)

        val boundaries = sortedSetOf(0) // 零点：日期类型/节假日可能变化。
        located.forEach { r ->
            r.conditions.filterIsInstance<Condition.TimeCondition>().forEach { t ->
                boundaries.add(t.startMinute.coerceIn(0, 1439))
                boundaries.add(t.endMinute.coerceIn(0, 1439))
            }
        }
        // 没有时间条件、也没有节假日条件 → 围栏长期有效，无需定时重评。
        val hasGating = located.any { r ->
            r.conditions.any { it is Condition.TimeCondition || it is Condition.HolidayCondition }
        }
        if (!hasGating) {
            am.cancel(pi)
            return
        }
        val deltaMin = boundaries
            .map { (it - now.minuteOfDay + 1440) % 1440 }
            .map { if (it == 0) 1440 else it }
            .minOrNull() ?: return
        val triggerAt = SystemClock.elapsedRealtime() + deltaMin * 60_000L
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        }.onFailure { Logger.w("geofence alarm failed: ${it.message}") }
    }

    private fun resyncIntent(app: Context): PendingIntent {
        val i = Intent(ACTION_RESYNC).setPackage(app.packageName)
        return PendingIntent.getBroadcast(
            app, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureSetup(app: Context): GeoFenceClient? {
        client?.let { return it }
        return runCatching {
            // 高德 SDK 隐私合规：必须先声明已展示隐私政策且用户同意，否则定位/围栏不工作。
            AMapLocationClient.updatePrivacyShow(app, true, true)
            AMapLocationClient.updatePrivacyAgree(app, true)

            val c = GeoFenceClient(app)
            c.setActivateAction(
                GeoFenceClient.GEOFENCE_IN or GeoFenceClient.GEOFENCE_OUT or GeoFenceClient.GEOFENCE_STAYED
            )
            c.createPendingIntent(ACTION_FENCE)

            val r = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    when (intent.action) {
                        ACTION_RESYNC -> resync(ctx.applicationContext)
                        ACTION_FENCE -> {
                            val b = intent.extras ?: return
                            val key = b.getString(GeoFence.BUNDLE_KEY_CUSTOMID) ?: return
                            when (b.getInt(GeoFence.BUNDLE_KEY_FENCESTATUS)) {
                                GeoFence.STATUS_IN, GeoFence.STATUS_STAYED -> GeofenceState.enter(key)
                                GeoFence.STATUS_OUT -> GeofenceState.exit(key)
                            }
                        }
                    }
                }
            }
            val filter = IntentFilter().apply { addAction(ACTION_FENCE); addAction(ACTION_RESYNC) }
            ContextCompat.registerReceiver(app, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiver = r
            client = c
            c
        }.onFailure { Logger.w("geofence setup failed: ${it.message}") }.getOrNull()
    }

    private fun teardown(app: Context) {
        runCatching { client?.removeGeoFence() }
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        (app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(resyncIntent(app))
        receiver = null
        client = null
    }

    /** 当前的时间上下文（与 RuleEngine 同一套判断口径）。 */
    private class TimeCtx(val minuteOfDay: Int, val isoDay: Int, val dayType: DayType) {
        companion object {
            fun now(): TimeCtx {
                val c = Calendar.getInstance()
                val iso = ((c.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
                val minute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
                val type = HolidayProvider.dayType(
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), iso,
                )
                return TimeCtx(minute, iso, type)
            }
        }
    }
}
