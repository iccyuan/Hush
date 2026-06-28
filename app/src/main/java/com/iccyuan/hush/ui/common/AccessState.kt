package com.iccyuan.hush.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.iccyuan.hush.service.BuzzKillListenerService
import com.iccyuan.hush.service.NotificationAccess

/** 每次屏幕恢复时重新评估通知监听器的访问权限。 */
@Composable
fun rememberNotificationAccessGranted(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(NotificationAccess.isGranted(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = NotificationAccess.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted
}

/**
 * 监听器是否真正处于已连接状态（区别于「已授权」——OEM 省电策略可能在授权后仍杀掉服务）。
 * 每次屏幕恢复时重新检查；若已授权但未连接，则顺手请求一次重新绑定以尝试恢复。
 */
@Composable
fun rememberListenerConnected(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var connected by remember { mutableStateOf(BuzzKillListenerService.isConnected()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                BuzzKillListenerService.requestRebindIfNeeded(context)
                connected = BuzzKillListenerService.isConnected()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return connected
}
