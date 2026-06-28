package com.iccyuan.hush.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.iccyuan.hush.util.Logger
import java.io.File

/**
 * 内置「下载 + 安装」更新包：用系统 [DownloadManager] 把发布 APK 下到应用私有外部目录
 * （无需存储权限），下载完成后经 [FileProvider] 拉起系统安装器——全程不经过浏览器。
 *
 * 下载期间会显示系统下载通知；若完成时无法直接拉起安装器（后台启动 Activity 限制），
 * 用户也可点该通知手动安装。
 */
object ApkInstaller {

    private const val APK_MIME = "application/vnd.android.package-archive"

    /** 是否为可直接下载的 APK 直链（否则应回退到浏览器打开发布页）。 */
    fun isApk(url: String): Boolean =
        url.substringBefore('?').endsWith(".apk", ignoreCase = true)

    /** 下载指定版本的 APK 并在完成后拉起安装。需在主线程调用（注册广播）。 */
    fun downloadAndInstall(context: Context, url: String, version: String) {
        val app = context.applicationContext
        val dm = app.getSystemService(DownloadManager::class.java) ?: return
        val fileName = "Hush-$version.apk"
        val dest = File(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        runCatching { if (dest.exists()) dest.delete() }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, fileName)
        val id = runCatching { dm.enqueue(request) }.getOrElse {
            Logger.w("apk download enqueue failed: ${it.message}")
            return
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != id) return
                runCatching { app.unregisterReceiver(this) }
                if (succeeded(dm, id)) install(app, dest)
                else Logger.w("apk download did not complete successfully")
            }
        }
        ContextCompat.registerReceiver(
            app, receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun succeeded(dm: DownloadManager, id: Long): Boolean {
        dm.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                return status == DownloadManager.STATUS_SUCCESSFUL
            }
        }
        return false
    }

    private fun install(context: Context, file: File) {
        if (!file.exists()) {
            Logger.w("apk file missing after download")
            return
        }
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse {
            Logger.w("fileprovider uri failed: ${it.message}")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Logger.w("apk install intent failed: ${it.message}") }
    }
}
