package com.iccyuan.notifytest

import android.util.Log

/**
 * 测试工具的统一日志出口。**不要**在别处直接调 `android.util.Log`——集中在这里，TAG 才唯一、
 * 过滤才方便（`adb logcat -s NotifyTest`），要加开关或改格式也只动这一处。
 *
 * 与主应用的 Logger 各自独立：testapp 是个可单独安装的独立模块，不依赖主应用的代码。
 */
object Logger {

    private const val TAG = "NotifyTest"

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(TAG, message, throwable) else Log.w(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
    }
}
