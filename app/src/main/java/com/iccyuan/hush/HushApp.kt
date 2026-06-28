package com.iccyuan.hush

import android.app.Application
import com.iccyuan.hush.data.HolidayProvider
import com.iccyuan.hush.data.RuntimeStateStore
import com.iccyuan.hush.service.ChannelManager
import com.iccyuan.hush.service.KeepAliveService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HushApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 在首条通知到达之前确保基础通知渠道已存在。
        ChannelManager(this).ensureBaseChannels()

        // 前台保活：把进程提升到前台服务优先级，降低监听器被省电策略杀死的概率。
        KeepAliveService.start(this)

        // 恢复持久化的运行时状态（冷却 / 静音 / 变量）。
        RuntimeStateStore.init(this)

        // 加载节假日数据，并以每周最多一次的频率从网络刷新（尽力而为）。
        HolidayProvider.ensureLoaded(this)
        val weekMs = 7L * 24 * 3600 * 1000
        if (System.currentTimeMillis() - HolidayProvider.lastUpdated(this) > weekMs) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching { HolidayProvider.refresh(this@HushApp) }
            }
        }
    }
}
