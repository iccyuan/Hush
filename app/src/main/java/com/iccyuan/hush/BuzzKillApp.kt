package com.iccyuan.hush

import android.app.Application
import com.iccyuan.hush.data.HolidayProvider
import com.iccyuan.hush.service.ChannelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BuzzKillApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 在首条通知到达之前确保基础通知渠道已存在。
        ChannelManager(this).ensureBaseChannels()

        // 恢复持久化的运行时状态（冷却 / 静音 / 变量）。
        com.iccyuan.hush.data.RuntimeStateStore.init(this)

        // 加载节假日数据，并以每周最多一次的频率从网络刷新（尽力而为）。
        HolidayProvider.ensureLoaded(this)
        val weekMs = 7L * 24 * 3600 * 1000
        if (System.currentTimeMillis() - HolidayProvider.lastUpdated(this) > weekMs) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching { HolidayProvider.refresh(this@BuzzKillApp) }
            }
        }
    }
}
