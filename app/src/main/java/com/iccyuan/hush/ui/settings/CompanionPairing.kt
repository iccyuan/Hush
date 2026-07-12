package com.iccyuan.hush.ui.settings

import android.app.Activity
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.iccyuan.hush.util.Logger

/**
 * 「系统级静音」的开通流程：向系统申请一个配套设备（CompanionDeviceManager）关联。
 *
 * 为什么需要它：把目标应用的通知渠道改成不发声不振动，是唯一能做到「完全静音 + 通知原样保留」
 * 的途径（见 [com.iccyuan.hush.service.ChannelSilencer]）。而 Android 对该 API 的准入条件就是
 * 「调用方持有一个配套设备关联」——这是系统给可穿戴/伴侣类应用开的口子，也是普通应用能拿到
 * 这项能力的唯一合法路径。
 *
 * 关联只是一次授权登记：用户从系统弹窗里挑一个附近的蓝牙设备（耳机、手表、音箱都行），
 * 之后不需要真的连上它，本应用也不会与之通信。
 */
object CompanionPairing {

    fun isPaired(context: Context): Boolean = runCatching {
        context.getSystemService(CompanionDeviceManager::class.java)?.associations?.isNotEmpty() == true
    }.getOrDefault(false)

    /** 撤销全部关联（用户关闭该能力时调用）。 */
    fun unpair(context: Context) {
        runCatching {
            val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return
            cdm.associations.forEach { address ->
                runCatching { cdm.disassociate(address) }
            }
        }.onFailure { Logger.e("companion: unpair failed", it) }
    }
}

/**
 * 记住一个「发起配套设备关联」的动作：调用返回的 lambda 即弹出系统设备选择器，
 * 用户选完后 [paired] 会翻成 true。
 */
@Composable
fun rememberCompanionPairing(
    context: Context,
    paired: MutableState<Boolean> = remember { mutableStateOf(CompanionPairing.isPaired(context)) },
): Pair<MutableState<Boolean>, () -> Unit> {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // 用户在系统选择器里确认后，关联即已登记；这里只需回读一次状态。
        if (result.resultCode == Activity.RESULT_OK) {
            paired.value = CompanionPairing.isPaired(context)
            Logger.i("companion: association result ok, paired=${paired.value}")
        }
    }

    val start: () -> Unit = start@{
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return@start
        // 不限定具体设备：列出附近所有蓝牙设备，用户挑哪个都行——我们只需要「有一个关联」这件事。
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().build())
            .setSingleDevice(false)
            .build()

        val callback = object : CompanionDeviceManager.Callback() {
            // Android 13+ 走这个回调；旧版本走下面的 onDeviceFound。
            override fun onAssociationPending(intentSender: IntentSender) {
                launcher.launch(IntentSenderRequest.Builder(intentSender).build())
            }

            @Deprecated("Superseded by onAssociationPending on T+", ReplaceWith(""))
            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onDeviceFound(intentSender: IntentSender) {
                launcher.launch(IntentSenderRequest.Builder(intentSender).build())
            }

            override fun onFailure(error: CharSequence?) {
                Logger.w("companion: association failed: $error")
            }
        }
        runCatching { cdm.associate(request, callback, null) }
            .onFailure { Logger.e("companion: associate() threw", it) }
    }

    return paired to start
}

/** 系统的「配套设备」管理页（用户可在此撤销关联）。 */
fun companionSettingsIntent(): Intent =
    Intent(android.provider.Settings.ACTION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

/** 该能力是否受本机支持（需要 Android 8.0+ 与配套设备特性）。 */
fun companionSupported(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_COMPANION_DEVICE_SETUP)
