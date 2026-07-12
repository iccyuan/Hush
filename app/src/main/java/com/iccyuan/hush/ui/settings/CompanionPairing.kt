package com.iccyuan.hush.ui.settings

import android.app.Activity
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.ContextWrapper
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import com.iccyuan.hush.util.Logger

/**
 * 「系统级静音」的开通流程：向系统申请一个配套设备（CompanionDeviceManager）关联。
 *
 * 为什么需要它：把目标应用的通知渠道改成不发声不振动，是唯一能做到「完全静音 + 通知原样保留」
 * 的途径（见 [com.iccyuan.hush.service.ChannelSilencer]）。而 Android 对该 API 的准入条件就是
 * 「调用方持有一个配套设备关联」——这是系统给伴侣类应用开的口子，也是普通应用能合法拿到这项
 * 能力的唯一路径。
 *
 * 关联只是一次授权登记：用户从系统弹窗里挑一个附近的蓝牙设备（耳机、手表、音箱都行），
 * 之后既不需要真的连上它，本应用也不会与之通信。设备扫描与选择全部由系统弹窗代劳，
 * 因此本应用**不需要**任何蓝牙或定位权限。
 */
object CompanionPairing {

    /** 该能力是否受本机支持（需要 Android 8.0+ 与配套设备特性）。 */
    fun isSupported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)

    fun isPaired(context: Context): Boolean = runCatching {
        context.getSystemService(CompanionDeviceManager::class.java)?.associations?.isNotEmpty() == true
    }.getOrDefault(false)

    /**
     * 弹出系统的配套设备选择器。用户选完后系统即登记关联——不需要在这里接收结果，
     * 界面回到前台时重新读一次 [isPaired] 即可（见设置页的 rememberOnResume）。
     *
     * [context] 必须能顺出宿主 Activity，调用方请传 `LocalView.current.context`：本应用为了
     * 多语言用 createConfigurationContext 覆盖了 LocalContext（见 ProvideAppLocale），那个
     * Context 是脱离 Activity 链的 ContextImpl——拿它既找不到 Activity，也拿不到
     * ActivityResultRegistryOwner（rememberLauncherForActivityResult 会直接崩）。
     */
    fun requestPairing(context: Context) {
        val activity = context.findActivity() ?: run {
            Logger.w("companion: no activity in context (${context.javaClass.name}); cannot show chooser")
            return
        }
        val cdm = activity.getSystemService(CompanionDeviceManager::class.java) ?: return
        // 不限定具体设备：列出附近所有蓝牙设备，用户挑哪个都行——我们只需要「存在一个关联」这件事。
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().build())
            .setSingleDevice(false)
            .build()

        val callback = object : CompanionDeviceManager.Callback() {
            // Android 13+ 走 onAssociationPending；更早的版本走 onDeviceFound。
            override fun onAssociationPending(intentSender: IntentSender) = launch(activity, intentSender)

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onDeviceFound(intentSender: IntentSender) = launch(activity, intentSender)

            override fun onFailure(error: CharSequence?) {
                Logger.w("companion: association failed: $error")
            }
        }
        runCatching { cdm.associate(request, callback, null) }
            .onFailure { Logger.e("companion: associate() threw", it) }
    }

    private fun launch(activity: Activity, sender: IntentSender) {
        runCatching {
            activity.startIntentSenderForResult(sender, REQUEST_CODE, null, 0, 0, 0)
        }.onFailure { Logger.e("companion: cannot start chooser", it) }
    }

    /** 撤销全部关联（用户关闭该能力时调用）。 */
    fun unpair(context: Context) {
        runCatching {
            val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return
            cdm.associations.forEach { address -> runCatching { cdm.disassociate(address) } }
        }.onFailure { Logger.e("companion: unpair failed", it) }
    }

    private const val REQUEST_CODE = 0xC0DE

    /** Compose 的 LocalContext 常是套了主题的 ContextWrapper，需要顺着链找到宿主 Activity。 */
    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
