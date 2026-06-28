package com.iccyuan.hush.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import com.iccyuan.hush.data.HolidayProvider
import com.iccyuan.hush.engine.DeviceContext
import java.util.Calendar

/** 采样供规则条件使用的环境设备状态。 */
object DeviceState {

    private val HEADPHONE_TYPES = intArrayOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_USB_HEADSET,
    )

    /**
     * @param sampleHeadphones / [sampleWifi] 仅当存在用到对应条件的启用规则时才置 true——
     * 否则跳过这些查询，不为每条通知做无谓的耳机/网络探测。
     */
    fun sample(
        context: Context,
        sampleHeadphones: Boolean = false,
        sampleWifi: Boolean = false,
    ): DeviceContext {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        // Calendar 中 SUNDAY=1..SATURDAY=7；转换为 ISO 标准 MONDAY=1..SUNDAY=7。
        val iso = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1

        HolidayProvider.ensureLoaded(context)
        val dayType = HolidayProvider.dayType(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH),
            isoDayOfWeek = iso,
        )

        val pm = context.getSystemService(PowerManager::class.java)
        val screenOn = pm?.isInteractive ?: true

        val batteryStatus = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        // 耳机：任一有线/蓝牙/USB 音频输出设备即视为已连接（无需权限）。仅在有规则用到时才探测。
        val headphones = if (sampleHeadphones) {
            context.getSystemService(AudioManager::class.java)
                ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any { it.type in HEADPHONE_TYPES } ?: false
        } else false

        // Wi-Fi：当前活动网络是否走 Wi-Fi 传输（只看传输类型，不读 SSID，无需定位权限）。仅在有规则用到时才查询。
        val onWifi = if (sampleWifi) {
            context.getSystemService(ConnectivityManager::class.java)
                ?.let { it.getNetworkCapabilities(it.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
                ?: false
        } else false

        return DeviceContext(
            charging = charging,
            screenOn = screenOn,
            batteryPercent = percent,
            minuteOfDay = minuteOfDay,
            isoDayOfWeek = iso,
            dayType = dayType,
            nowMillis = now,
            headphonesConnected = headphones,
            onWifi = onWifi,
        )
    }
}
