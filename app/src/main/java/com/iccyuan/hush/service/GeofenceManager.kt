package com.iccyuan.hush.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.amap.api.fence.GeoFence
import com.amap.api.fence.GeoFenceClient
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.DPoint
import com.iccyuan.hush.data.model.Condition
import com.iccyuan.hush.data.model.Rule
import com.iccyuan.hush.util.Logger

/**
 * 用高德 [GeoFenceClient] 注册/同步规则用到的地理围栏。进出由系统级围栏监控（不轮询、省电），
 * 通过广播回调把"在/不在"写入 [GeofenceState]，规则条件再从中读取——不依赖 Google Play 服务。
 */
object GeofenceManager {

    private const val ACTION = "com.iccyuan.hush.action.GEOFENCE"

    private var client: GeoFenceClient? = null
    private var receiver: BroadcastReceiver? = null
    private var registeredKeys: Set<String> = emptySet()

    /** 按当前启用规则里的位置条件，增量同步围栏；没有则全部移除。 */
    @Synchronized
    fun sync(context: Context, rules: List<Rule>) {
        val app = context.applicationContext
        val fences = rules.asSequence()
            .flatMap { it.conditions.asSequence() }
            .filterIsInstance<Condition.LocationCondition>()
            .filter { it.latitude != 0.0 || it.longitude != 0.0 }
            .distinctBy { it.fenceKey() }
            .toList()
        val keys = fences.map { it.fenceKey() }.toSet()
        if (keys == registeredKeys && (client != null || keys.isEmpty())) return
        registeredKeys = keys

        if (fences.isEmpty()) {
            teardown(app)
            GeofenceState.reset()
            return
        }
        val c = ensureSetup(app) ?: return
        runCatching {
            c.removeGeoFence()
            fences.forEach { f ->
                c.addGeoFence(DPoint(f.latitude, f.longitude), f.radiusMeters.toFloat(), f.fenceKey())
            }
        }.onFailure { Logger.w("geofence sync failed: ${it.message}") }
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
            c.createPendingIntent(ACTION)

            val r = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action != ACTION) return
                    val b = intent.extras ?: return
                    val key = b.getString(GeoFence.BUNDLE_KEY_CUSTOMID) ?: return
                    when (b.getInt(GeoFence.BUNDLE_KEY_FENCESTATUS)) {
                        GeoFence.STATUS_IN, GeoFence.STATUS_STAYED -> GeofenceState.enter(key)
                        GeoFence.STATUS_OUT -> GeofenceState.exit(key)
                    }
                }
            }
            ContextCompat.registerReceiver(app, r, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiver = r
            client = c
            c
        }.onFailure { Logger.w("geofence setup failed: ${it.message}") }.getOrNull()
    }

    private fun teardown(app: Context) {
        runCatching { client?.removeGeoFence() }
        receiver?.let { runCatching { app.unregisterReceiver(it) } }
        receiver = null
        client = null
    }
}
