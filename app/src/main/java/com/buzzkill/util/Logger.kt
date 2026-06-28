package com.buzzkill.util

import android.util.Log

/**
 * 全应用统一的日志入口。集中固定 TAG 与调用形式，避免到处散落
 * `android.util.Log.x(TAG, ...)` 这类冗长、可读性差的写法，也便于日后统一加开关、
 * 格式化或转发到文件。
 *
 * 用法：`Logger.i("listener connected")` / `Logger.e("repost failed", t)`。
 */
object Logger {

    private const val TAG = "Hush"

    fun d(message: String) = Log.d(TAG, message).let {}

    fun i(message: String) = Log.i(TAG, message).let {}

    fun w(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(TAG, message, throwable) else Log.w(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
    }
}
