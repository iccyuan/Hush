package com.buzzkill.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable

/** 在规则的应用选择器中显示的用户安装（或发送通知）的应用。 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

object InstalledApps {

    // 应用列表很少变化，但解析每个应用的标签和图标开销不小。按 includeSystem 维度缓存，
    // 避免每次打开选择器都全量重新加载。包安装/卸载时调用 [invalidate] 失效。
    private val cache = java.util.concurrent.ConcurrentHashMap<Boolean, List<AppInfo>>()

    /** 清除缓存（例如在应用安装/卸载后）。 */
    fun invalidate() = cache.clear()

    /**
     * 返回按标签排序的已安装应用，排除本应用自身。当
     * [includeSystem] 为 false（默认）时，没有启动器入口的系统应用会被隐藏。
     * 由调用方在非主线程中加载。结果会被缓存；传入 [forceReload] 可强制刷新。
     */
    fun load(context: Context, includeSystem: Boolean = false, forceReload: Boolean = false): List<AppInfo> {
        if (!forceReload) cache[includeSystem]?.let { return it }
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(0)
        return installed
            .asSequence()
            .filter { it.packageName != context.packageName }
            // 仅保留用户可见的应用：可启动的、非系统的，或已更新的系统应用。
            .filter { info ->
                includeSystem ||
                    pm.getLaunchIntentForPackage(info.packageName) != null ||
                    (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            }
            .map { info ->
                AppInfo(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = runCatching { pm.getApplicationIcon(info) }.getOrNull(),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
            .also { cache[includeSystem] = it }
    }

    /** 解析指定包名的标签和图标（已卸载的包会被剔除）。 */
    fun infoFor(context: Context, packages: List<String>): List<AppInfo> {
        val pm = context.packageManager
        return packages.mapNotNull { pkg ->
            runCatching {
                val ai = pm.getApplicationInfo(pkg, 0)
                AppInfo(pkg, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai))
            }.getOrNull()
        }
    }
}
