package com.iccyuan.hush.service

import java.util.concurrent.ConcurrentHashMap

/**
 * 按 key 的求值节流：同一个 key 在 [intervalMs] 内只放行一次。
 *
 * 用于常驻通知（VPN / 播放器 / 下载）——它们会秒级重发同一条通知来刷新里面的数字（实测某 VPN
 * 每秒一次，一天八万多次），每次都重跑一遍规则纯属白烧电。这类通知表示的是**持续状态**而非
 * 事件，隔一会儿重算一次足矣。
 *
 * 时钟由调用方传入（[now]），从而与 Android 的 SystemClock 解耦、可直接单元测试——这段逻辑
 * 若写错，后果是「针对常驻通知的规则完全失效」，不能只靠肉眼审读。
 */
class EvalThrottle(private val intervalMs: Long) {

    private val lastEval = ConcurrentHashMap<String, Long>()

    /** [key] 此刻是否该重新求值；放行的同时记下时刻。 */
    fun due(key: String, now: Long): Boolean {
        val last = lastEval[key]
        if (last != null && now - last < intervalMs) return false
        lastEval[key] = now
        return true
    }

    /** 通知已消失，其节流记录一并丢弃（否则常驻通知反复重建时会一直攒着旧 key）。 */
    fun forget(key: String) {
        lastEval.remove(key)
    }
}
