package com.buzzkill.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import com.buzzkill.data.HolidayProvider
import com.buzzkill.engine.DeviceContext
import java.util.Calendar

/** 采样供规则条件使用的环境设备状态。 */
object DeviceState {

    fun sample(context: Context): DeviceContext {
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

        return DeviceContext(
            charging = charging,
            screenOn = screenOn,
            batteryPercent = percent,
            minuteOfDay = minuteOfDay,
            isoDayOfWeek = iso,
            dayType = dayType,
            nowMillis = now,
        )
    }
}
