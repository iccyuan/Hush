package com.iccyuan.hush.data

import com.iccyuan.hush.data.db.BuzzJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.HttpURLConnection
import java.net.URL

/**
 * 通过 GitHub Releases API 检查是否有新版本。
 * 应用以 side-load 的 APK 形式经 GitHub Release 分发，这里只做「检查 + 给出下载链接」，
 * 不在应用内静默安装。
 */
object UpdateChecker {

    private const val LATEST_API =
        "https://api.github.com/repos/iccyuan/Hush/releases/latest"

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tagName: String = "",
        @SerialName("html_url") val htmlUrl: String = "",
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String = "",
        @SerialName("browser_download_url") val downloadUrl: String = "",
    )

    /** 检查结果。downloadUrl 优先指向 APK 资源直链，缺失时回退到 Release 页面。 */
    data class Result(
        val hasUpdate: Boolean,
        val latestVersion: String,
        val currentVersion: String,
        val downloadUrl: String,
    )

    /** 查询最新 Release 并与当前版本比较。网络/解析失败返回 null（由调用方提示）。 */
    fun check(currentVersion: String): Result? = runCatching {
        val conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Hush")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val release = BuzzJson.decodeFromString(Release.serializer(), body)
        val latest = release.tagName.removePrefix("v").trim()
        if (latest.isEmpty()) return@runCatching null

        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?.downloadUrl?.takeIf { it.isNotBlank() }

        Result(
            hasUpdate = isNewer(latest, currentVersion),
            latestVersion = latest,
            currentVersion = currentVersion,
            downloadUrl = apk ?: release.htmlUrl,
        )
    }.getOrNull()

    /** 语义化版本比较：latest 严格大于 current 时返回 true。缺失/非数字段按 0 处理。 */
    private fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
