package com.iccyuan.hush.util

import android.util.Log
import com.iccyuan.hush.BuildConfig

/**
 * 全应用统一的日志入口。**不要**在别处直接调 `android.util.Log`——集中在这里才能统一 TAG、
 * 统一开关、统一格式，日后要落盘或上报也只改这一处。
 *
 * ## 用哪一档
 *
 * - [d]：诊断细节。**仅 debug 构建输出**。热路径上的日志（每条通知都会打的那种）一律用它——
 *   正式版里没人看，却在白烧电，还会把 logcat 冲爆，把真正要查的东西挤掉。
 * - [i]：值得留在正式版里的里程碑事件（监听器连上了、规则加载了、静音落实了）。要节制。
 * - [w] / [e]：出了岔子。永远输出。
 *
 * ## 分类前缀
 *
 * 用 [scoped] 给一个子系统固定一个前缀，logcat 里就能一眼挑出来：
 * ```
 * private val log = Logger.scoped("silence")   // → "silence: snoozed 0|com.foo|..."
 * log.i("snoozed $key")
 * ```
 * 过滤：`adb logcat -s Hush | grep silence`
 */
object Logger : Sink(prefix = "") {

    internal const val TAG = "Hush"

    /** 给某个子系统的日志固定一个前缀，便于在 logcat 里筛出来。 */
    fun scoped(prefix: String): Sink = Sink("$prefix: ")
}

/** 日志出口。[Logger] 自身即一个无前缀的出口；[Logger.scoped] 生成带前缀的出口。 */
open class Sink internal constructor(private val prefix: String) {

    /** 诊断细节，仅 debug 构建输出。热路径上的日志用这一档。 */
    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(Logger.TAG, prefix + message)
    }

    /** 里程碑事件，正式版里也会输出——所以要克制，别放进热路径。 */
    fun i(message: String) {
        Log.i(Logger.TAG, prefix + message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(Logger.TAG, prefix + message, throwable)
        else Log.w(Logger.TAG, prefix + message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(Logger.TAG, prefix + message, throwable)
        else Log.e(Logger.TAG, prefix + message)
    }
}
