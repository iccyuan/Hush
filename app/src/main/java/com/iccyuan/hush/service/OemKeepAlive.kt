package com.iccyuan.hush.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * OEM 保活相关的检测与设置深链。国产 ROM（ColorOS/MIUI/EMUI/…）的省电策略会杀掉通知监听，
 * 需引导用户开启「自启动、后台运行、电池优化白名单」等。各厂商的设置 Activity 名不稳定，
 * 因此对每个厂商准备若干候选，取第一个可解析的；找不到就回退到应用详情页。
 */
object OemKeepAlive {

    enum class Oem { COLOROS, MIUI, EMUI, VIVO, SAMSUNG, MEIZU, OTHER }

    private val brand: String get() = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()

    fun oem(): Oem = when {
        listOf("xiaomi", "redmi", "poco").any { brand.contains(it) } -> Oem.MIUI
        listOf("oppo", "oneplus", "realme").any { brand.contains(it) } -> Oem.COLOROS
        listOf("huawei", "honor").any { brand.contains(it) } -> Oem.EMUI
        listOf("vivo", "iqoo").any { brand.contains(it) } -> Oem.VIVO
        brand.contains("samsung") -> Oem.SAMSUNG
        brand.contains("meizu") -> Oem.MEIZU
        else -> Oem.OTHER
    }

    /** 面向用户的厂商标识（用于向导标题）。 */
    fun oemLabel(): String = when (oem()) {
        Oem.COLOROS -> "ColorOS"
        Oem.MIUI -> "MIUI / HyperOS"
        Oem.EMUI -> "EMUI / HarmonyOS"
        Oem.VIVO -> "Funtouch / OriginOS"
        Oem.SAMSUNG -> "One UI"
        Oem.MEIZU -> "Flyme"
        Oem.OTHER -> Build.MANUFACTURER
    }

    /** 是否已加入电池优化白名单（可检测）。 */
    fun isIgnoringBattery(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
    }

    /**
     * 请求加入电池优化白名单：先尝试标准直接授权弹窗（需 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 权限），
     * 失败则回退到系统「电池优化」列表（所有 Android 都有），再兜底应用详情页。
     */
    fun openBattery(context: Context) {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(direct) }.isSuccess) return
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(list) }.isSuccess) return
        runCatching { context.startActivity(appDetailsIntent(context)) }
    }

    /** 打开 OEM「自启动」设置页；逐个候选尝试，全失败则回退到应用详情页。 */
    fun openAutostart(context: Context) = openFirst(context, autostartCandidates())

    /** 打开 OEM「后台运行 / 省电策略」设置页；同上。 */
    fun openBackground(context: Context) = openFirst(context, backgroundCandidates())

    /** 兜底：应用详情页（所有系统都有），用户可从中找到自启动/耗电等选项。 */
    fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun autostartCandidates(): List<ComponentName> = when (oem()) {
        Oem.MIUI -> listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        Oem.COLOROS -> listOf(
            // 新版 ColorOS（13+）：自启动移到 com.oplus.battery（新机可能因系统权限拒绝，靠 openFirst 回退）。
            ComponentName("com.oplus.battery", "com.oplus.startupapp.view.StartupAppListActivity"),
            ComponentName("com.oplus.battery", "com.oplus.startupapp.view.OptimizationAutoStartActivity"),
            // 旧版 ColorOS / OPPO。
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        )
        Oem.EMUI -> listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )
        Oem.VIVO -> listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        )
        Oem.MEIZU -> listOf(
            ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"),
        )
        else -> emptyList()
    }

    private fun backgroundCandidates(): List<ComponentName> = when (oem()) {
        Oem.COLOROS -> listOf(
            ComponentName("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerConsumptionActivity"),
            ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        )
        Oem.MIUI -> listOf(
            ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
        )
        Oem.VIVO -> listOf(
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        )
        else -> emptyList()
    }

    /**
     * 逐个候选直接 startActivity（比 resolveActivity 预检可靠——不少 OEM 的设置 Activity 未导出，
     * resolveActivity 返回 null 但仍可被系统设置进程接受）；全部失败则回退到应用详情页。
     */
    private fun openFirst(context: Context, candidates: List<ComponentName>) {
        for (cn in candidates) {
            val intent = Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
        runCatching { context.startActivity(appDetailsIntent(context)) }
    }
}
