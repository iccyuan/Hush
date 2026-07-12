package com.iccyuan.notifytest

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hush 的通知测试工具：手动发送「会响铃震动」的通知，验证静音/改写规则的真实效果。
 *
 * 也可由 adb 无界面触发（发完即退出）：
 *   adb shell am start -n com.iccyuan.notifytest/.MainActivity --es tag t1 --es title Hi [--ez once true]
 *   adb shell am start -n com.iccyuan.notifytest/.MainActivity --es mode play   # 播放默认铃声（探针校准）
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // adb 自动化路径：带 extras 时执行动作后直接退出，不显示界面。
        if (intent.getStringExtra("mode") == "play") {
            RingtoneManager.getRingtone(this, Settings.System.DEFAULT_RINGTONE_URI)?.play()
            return // 保持 Activity 存活让铃声播完；由调用方 force-stop。
        }
        intent.getStringExtra("tag")?.let { tag ->
            Notifier.post(
                this,
                tag = tag,
                title = intent.getStringExtra("title") ?: tag,
                text = intent.getStringExtra("text") ?: "测试通知",
                alertOnce = intent.getBooleanExtra("once", false),
            )
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        setContentView(buildUi())
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "每次发送都会用新的 tag，正常情况下必响铃震动；" +
                "被 Hush 静音后应完全无声，且通知仍留在通知栏。\n\n" +
                "ColorOS 等系统需先在本应用的通知设置里打开「横幅」，否则系统本身就不响铃。"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        })

        root.button("立即发送（应响铃+震动）") { postNow() }
        root.button("延迟 5 秒发送（先锁屏/退后台）") {
            Toast.makeText(this, "5 秒后发送，请锁屏或退到后台", Toast.LENGTH_SHORT).show()
            handler.postDelayed({ postNow() }, 5_000)
        }
        root.button("连发 3 条（间隔 3 秒）") {
            Toast.makeText(this, "将在后台连发 3 条", Toast.LENGTH_SHORT).show()
            repeat(3) { i -> handler.postDelayed({ postNow() }, i * 3_000L) }
        }
        root.button("更新上一条（仅首次提醒，应静默）") {
            val tag = lastTag
            if (tag == null) {
                Toast.makeText(this, "请先发送一条通知", Toast.LENGTH_SHORT).show()
            } else {
                Notifier.post(this, tag, "更新 $tag", "同 key 静默更新 ${now()}", alertOnce = true)
            }
        }
        root.button("打开本应用通知设置（检查横幅开关）") {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }

        return ScrollView(this).apply { addView(root) }
    }

    private fun postNow() {
        val tag = "t${System.currentTimeMillis() % 100_000}"
        lastTag = tag
        Notifier.post(this, tag, "测试通知 $tag", "发送于 ${now()}")
    }

    private fun now(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private fun LinearLayout.button(label: String, onClick: () -> Unit) {
        addView(Button(context).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(12) })
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
