package com.iccyuan.hush.service

/**
 * 进程内缓存"当前处于哪些地理围栏内"——由 [GeofenceManager] 依据高德围栏的进出广播更新，
 * 规则条件经 [DeviceState] 读取。如此每条通知只读内存集合，不触发定位，省电。
 */
object GeofenceState {

    @Volatile
    var inside: Set<String> = emptySet()
        private set

    fun enter(key: String) {
        inside = inside + key
    }

    fun exit(key: String) {
        inside = inside - key
    }

    fun reset() {
        inside = emptySet()
    }
}
