package com.iccyuan.hush.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import com.iccyuan.hush.data.DanmakuConfig
import com.iccyuan.hush.util.Logger

/**
 * 通过悬浮窗在屏幕顶部显示滚动的「弹幕」文字：每条从最右边缘匀速滑到左侧屏幕外后自行移除。
 *
 * 外观与行为由全局 [DanmakuConfig] 驱动（字号 / 颜色 / 背景透明度 / 速度 / 行数 / 顶部偏移），
 * 由服务在设置变化时通过 [updateConfig] 推入。为避免刷屏与重叠：
 *  - **去重**：相同文字在 [DEDUP_MS] 内只显示一次。
 *  - **排队分行**：为每行维护「下一条可进入的时刻」，新弹幕分配到最早空闲的行，必要时延后进入，
 *    使同一行的弹幕保持间距、互不重叠；积压过久则丢弃，避免无限排队。
 *
 * 需要「在其他应用上层显示」权限。
 */
object DanmakuController {

    private const val DEDUP_MS = 2500L
    private const val MAX_QUEUE_DELAY = 8000L

    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var config = DanmakuConfig()

    // 以下状态仅在主线程访问。
    private var rowFreeAt = LongArray(config.rows)
    private val inflight = HashMap<String, Int>()   // 相同文字：当前排队中 + 屏幕上的条数
    private val recentEnd = HashMap<String, Long>()  // 相同文字：上次离场时刻（离场后短暂冷却）

    /** 由服务在设置变化时调用，更新全局弹幕外观/行为。 */
    fun updateConfig(c: DanmakuConfig) {
        config = c
        main.post { if (rowFreeAt.size != c.rows) rowFreeAt = LongArray(c.rows) }
    }

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(context: Context, text: String) {
        if (text.isBlank()) return
        if (!canShow(context)) {
            // 未授予悬浮窗权限——规则虽匹配，但弹幕无法显示。在编辑器/设置里会提示授权。
            Logger.w("danmaku skipped: overlay permission not granted")
            return
        }
        val app = context.applicationContext
        main.post { enqueue(app, text) }
    }

    private fun enqueue(app: Context, text: String) {
        val cfg = config
        val now = SystemClock.uptimeMillis()

        // 同内容不重复显示：相同文字若仍在排队/屏幕上，或刚离场不久（冷却窗内），则跳过。
        if ((inflight[text] ?: 0) > 0) return
        recentEnd.entries.removeAll { now - it.value > DEDUP_MS }
        recentEnd[text]?.let { if (now - it < DEDUP_MS) return }

        if (rowFreeAt.size != cfg.rows) rowFreeAt = LongArray(cfg.rows)
        // 选最早空闲的行。
        var row = 0
        for (i in 1 until cfg.rows) if (rowFreeAt[i] < rowFreeAt[row]) row = i
        val startAt = maxOf(now, rowFreeAt[row])
        val delay = startAt - now
        if (delay > MAX_QUEUE_DELAY) return // 积压过久：丢弃这一条，避免无限排队。
        // 预留该行下一条的最早进入时刻（约为时长的 42%，保证同行两条之间留有间距）。
        rowFreeAt[row] = startAt + (cfg.durationMs * 42 / 100).coerceAtLeast(700)
        // 占位：从此刻起到该弹幕离场，相同文字都会被去重跳过。
        inflight[text] = (inflight[text] ?: 0) + 1
        main.postDelayed({ showOnRow(app, text, row, cfg) }, delay)
    }

    /** 一条弹幕离场（正常结束或添加失败）时调用：释放同文字占位并记录离场时刻用于冷却。 */
    private fun endText(text: String) {
        val c = (inflight[text] ?: 1) - 1
        if (c <= 0) inflight.remove(text) else inflight[text] = c
        recentEnd[text] = SystemClock.uptimeMillis()
    }

    private fun showOnRow(app: Context, text: String, row: Int, cfg: DanmakuConfig) {
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val metrics = app.resources.displayMetrics
        val density = metrics.density
        val screenWidth = metrics.widthPixels

        val padH = (cfg.fontSizeSp * density * 0.85f).toInt()
        val padV = (cfg.fontSizeSp * density * 0.34f).toInt()

        val tv = TextView(app).apply {
            this.text = text
            setTextColor(cfg.color)
            textSize = cfg.fontSizeSp
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setSingleLine()
            letterSpacing = 0.01f
            // 半透明深色圆角胶囊：上浅下深的细微渐变 + 一圈淡白描边，做出「玻璃」质感。
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = density * 20
                val a = cfg.bgAlpha.coerceIn(0, 255)
                colors = intArrayOf(
                    Color.argb((a * 0.82f).toInt().coerceIn(0, 255), 28, 28, 32),
                    Color.argb(a, 0, 0, 0),
                )
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke((density * 0.8f).toInt().coerceAtLeast(1), Color.argb(46, 255, 255, 255))
            }
            setPadding(padH, padV, padH, padV)
            // 在 addView 前就定位到右边缘外并隐藏，避免布局前在 x=0 画出一帧造成闪烁。
            translationX = screenWidth.toFloat()
            alpha = 0f
        }

        // 全屏宽的透明容器承载弹幕，使胶囊能在整屏范围内平移而不被窗口裁剪。
        val container = FrameLayout(app).apply {
            addView(tv, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val rowHeight = (cfg.fontSizeSp * density * 1.35f + padV * 2 + density * 8f).toInt()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            y = (cfg.topOffsetDp * density).toInt() + row * rowHeight
        }

        runCatching { wm.addView(container, lp) }.onFailure {
            // 某些 OEM（如 ColorOS）即使已授予「显示在其他应用上层」，仍会拦截后台进程绘制悬浮窗，
            // 需额外开启「后台弹出界面」权限。记录原因便于排查。
            Logger.w("danmaku addView failed: ${it.message}")
            endText(text)
            return
        }

        tv.post {
            val start = container.width.toFloat().coerceAtLeast(screenWidth.toFloat())
            val end = -tv.width.toFloat()
            // 恒定速度：以「屏宽 / durationMs」为基准速度，实际时长按总行程(屏宽 + 文字宽度)等比缩放。
            // 这样长短不一、数量多少的弹幕都以**同一速度**滚动，速度不再随文字长度/通知条数变化。
            val speedPxPerMs = screenWidth.toFloat() / cfg.durationMs.coerceAtLeast(1L).toFloat()
            val duration = ((start - end) / speedPxPerMs).toLong().coerceIn(1200L, 30000L)
            tv.translationX = start
            tv.alpha = 1f
            tv.animate()
                .translationX(end)
                .setDuration(duration)
                .setInterpolator(LinearInterpolator())
                .withEndAction {
                    runCatching { wm.removeView(container) }
                    endText(text)
                }
                .start()
        }
    }

    fun overlaySettingsIntent(context: Context) =
        android.content.Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}"),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
}
