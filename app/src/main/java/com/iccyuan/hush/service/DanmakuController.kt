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
 *  - **排队分行**：为每行维护「下一条可进入的时刻」，新弹幕分配到最早空闲的行，必要时延后进入。
 *    进入时刻按这条弹幕的**实测胶囊宽度**预留（见 [entryClearMs]）：尾部离开右缘、再留出行内
 *    间距后，下一条才能进入——同一行因此成为真正的队列，长文字也不会被追尾重叠。
 *    积压过久则丢弃，避免无限排队。
 *
 * 需要「在其他应用上层显示」权限。
 */
object DanmakuController {

    private const val DEDUP_MS = 2500L
    private const val MAX_QUEUE_DELAY = 8000L

    /** 同一行相邻两条弹幕之间的最小间距（占屏宽比例）。 */
    private const val ROW_GAP_FRACTION = 0.08f

    /** 同一行相邻两条的最小时间间隔下限（毫秒），防止极短文字挤成串。 */
    private const val MIN_ROW_INTERVAL_MS = 700L

    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var config = DanmakuConfig()

    /** 等待进入某一行的一条弹幕。 */
    private class Pending(val app: Context, val text: String, val cfg: DanmakuConfig, val enqueuedAt: Long)

    // 以下状态仅在主线程访问。
    /** 每行入口的「预计空闲时刻」——只用于选行和积压丢弃的估算；真正的放行由行队列驱动。 */
    private var rowEstFreeAt = LongArray(config.rows)
    /** 每行的等待队列：入口被占用时后续弹幕在此排队，前一条尾部离开入口后依次放行。 */
    private var rowQueues = Array(config.rows) { ArrayDeque<Pending>() }
    /** 每行入口当前是否被占用（有弹幕正在进入，或队列尚未排空）。 */
    private var rowBusy = BooleanArray(config.rows)
    private val inflight = HashMap<String, Int>()   // 相同文字：当前排队中 + 屏幕上的条数
    private val recentEnd = HashMap<String, Long>()  // 相同文字：上次离场时刻（离场后短暂冷却）

    // 常驻悬浮窗容器：懒创建一次，后续弹幕仅作为子 View 增删，而非每条弹幕各开一个 WindowManager
    // 窗口——通知密集时后者会造成明显的 surface 分配/销毁开销。空容器（无弹幕时）本身开销可忽略。
    private var overlayRoot: FrameLayout? = null

    /**
     * 悬浮窗是否已创建；持有以便在弹幕清空时拆除窗口（见 [showOnRow] 的 withEndAction）。
     * 该悬浮窗即使自身 FLAG_NOT_TOUCHABLE 不拦截触摸，只要它还覆盖在屏幕上，就会让开启了
     * `setFilterTouchesWhenObscured` 的控件（不少银行/支付/系统权限弹窗都会开）静默丢弃触摸——
     * 因此绝不能让它在无弹幕时也常驻，否则会造成"用过一次弹幕后，某些应用再也点不动"。
     */
    private var windowManager: WindowManager? = null

    /** 确保悬浮窗已创建；已存在则直接复用。失败（如权限被收回）返回 null。 */
    private fun ensureOverlay(app: Context): FrameLayout? {
        overlayRoot?.let { return it }
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return null
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        val root = FrameLayout(app)
        return runCatching { wm.addView(root, lp); root }
            .onSuccess { overlayRoot = it; windowManager = wm }
            .onFailure { Logger.w("danmaku overlay create failed: ${it.message}") }
            .getOrNull()
    }

    /** 弹幕清空（最后一条离场）时调用：拆除悬浮窗，避免其在无弹幕时也常驻覆盖全屏。 */
    private fun teardownOverlayIfEmpty() {
        val root = overlayRoot ?: return
        if (root.childCount > 0) return
        val wm = windowManager ?: return
        runCatching { wm.removeView(root) }.onFailure { Logger.w("danmaku overlay teardown failed: ${it.message}") }
        overlayRoot = null
        windowManager = null
    }

    /** 由服务在设置变化时调用，更新全局弹幕外观/行为。 */
    fun updateConfig(c: DanmakuConfig) {
        config = c
        main.post { if (rowEstFreeAt.size != c.rows) resetRows(c.rows) }
    }

    /** 行数变化时重建各行状态；排队中尚未上屏的弹幕直接丢弃（并释放其去重占位）。 */
    private fun resetRows(rows: Int) {
        rowQueues.forEach { q -> q.forEach { endText(it.text) } }
        rowEstFreeAt = LongArray(rows)
        rowQueues = Array(rows) { ArrayDeque() }
        rowBusy = BooleanArray(rows)
    }

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** [force] = true 时绕过「同内容去重」，无论如何都显示（用于「预览」这类显式操作）。 */
    fun show(context: Context, text: String, force: Boolean = false) {
        if (text.isBlank()) return
        if (!canShow(context)) {
            // 未授予悬浮窗权限——规则虽匹配，但弹幕无法显示。在编辑器/设置里会提示授权。
            Logger.w("danmaku skipped: overlay permission not granted")
            return
        }
        val app = context.applicationContext
        main.post { enqueue(app, text, force) }
    }

    private fun enqueue(app: Context, text: String, force: Boolean) {
        val cfg = config
        val now = SystemClock.uptimeMillis()

        // 同内容不重复显示：相同文字若仍在排队/屏幕上，或刚离场不久（冷却窗内），则跳过。
        // 「预览」等显式操作用 force 跳过该去重，保证每次点击都显示。
        if (!force) {
            if ((inflight[text] ?: 0) > 0) return
            recentEnd.entries.removeAll { now - it.value > DEDUP_MS }
            recentEnd[text]?.let { if (now - it < DEDUP_MS) return }
        }

        if (rowEstFreeAt.size != cfg.rows) resetRows(cfg.rows)
        // 选预计最早空闲的行。
        var row = 0
        for (i in 1 until cfg.rows) if (rowEstFreeAt[i] < rowEstFreeAt[row]) row = i
        if (rowEstFreeAt[row] - now > MAX_QUEUE_DELAY) return // 积压过久：丢弃这一条，避免无限排队。
        // 估算下一条的最早进入时刻：这条的尾部离开右缘、再留出行内间距之后。
        // （以前按固定「42% 时长」预留，胶囊一旦宽过约四成屏宽就会被下一条追尾重叠。）
        rowEstFreeAt[row] = maxOf(now, rowEstFreeAt[row]) + entryClearMs(app, text, cfg)
        // 占位：从此刻起到该弹幕离场，相同文字都会被去重跳过。
        inflight[text] = (inflight[text] ?: 0) + 1
        val item = Pending(app, text, cfg, now)
        // 入口空闲则立刻进入，否则排到该行队尾——真正的放行时机由 openRowEntry 按
        // **前一条动画实际开始的时刻**推算，窗口首建/布局的延迟不会蚕食行内间距。
        if (rowBusy[row]) rowQueues[row].addLast(item) else { rowBusy[row] = true; showOnRow(item, row) }
    }

    /** 某行入口空出（前一条尾部已离开右缘并留出间距）：放行队列中的下一条；排队过久的丢弃。 */
    private fun openRowEntry(row: Int) {
        if (row >= rowQueues.size) return // 行数中途调小：该行已不存在。
        val now = SystemClock.uptimeMillis()
        while (true) {
            val next = rowQueues[row].removeFirstOrNull()
            if (next == null) { rowBusy[row] = false; return }
            if (now - next.enqueuedAt > MAX_QUEUE_DELAY) { endText(next.text); continue }
            showOnRow(next, row)
            return
        }
    }

    /** 胶囊的水平内边距（px）。抽出来是为了让 [entryClearMs] 的宽度估算与实际渲染保持一致。 */
    private fun pillPadH(cfg: DanmakuConfig, density: Float): Int =
        (cfg.fontSizeSp * density * 0.85f).toInt()

    /**
     * 这条弹幕从开始进入到「尾部离开右缘并留出行内间距」所需的毫秒数——在此之前同一行
     * 不能放下一条，否则后车必然追上前车的尾部。宽度用与实际 TextView 相同的字号/字重/
     * 字距实测；速度与 [showOnRow] 一致（屏宽 / durationMs，恒定速度）。
     */
    private fun entryClearMs(app: Context, text: String, cfg: DanmakuConfig): Long {
        val metrics = app.resources.displayMetrics
        val paint = android.text.TextPaint().apply {
            textSize = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP, cfg.fontSizeSp, metrics,
            )
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.01f
        }
        val pillWidth = paint.measureText(text) + pillPadH(cfg, metrics.density) * 2
        val gap = metrics.widthPixels * ROW_GAP_FRACTION
        val speedPxPerMs = metrics.widthPixels.toFloat() / cfg.durationMs.coerceAtLeast(1L).toFloat()
        return ((pillWidth + gap) / speedPxPerMs).toLong().coerceAtLeast(MIN_ROW_INTERVAL_MS)
    }

    /** 一条弹幕离场（正常结束或添加失败）时调用：释放同文字占位并记录离场时刻用于冷却。 */
    private fun endText(text: String) {
        val c = (inflight[text] ?: 1) - 1
        if (c <= 0) inflight.remove(text) else inflight[text] = c
        recentEnd[text] = SystemClock.uptimeMillis()
    }

    private fun showOnRow(item: Pending, row: Int) {
        val app = item.app
        val text = item.text
        val cfg = item.cfg
        val root = ensureOverlay(app) ?: run {
            endText(text)
            openRowEntry(row) // 失败也要放行该行，否则队列会卡死。
            return
        }
        val metrics = app.resources.displayMetrics
        val density = metrics.density
        val screenWidth = metrics.widthPixels

        val padH = pillPadH(cfg, density)
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

        // 弹幕作为子 View 加入常驻悬浮窗（[ensureOverlay]），而非各自新开一个窗口；
        // topMargin 决定其所在行，translationX 负责水平滚动。
        //
        // 宽度必须是**无约束实测后的显式值**：WRAP_CONTENT 会被父容器按 AT_MOST 钳到
        // 一个屏宽，超出的文字被 singleLine 直接裁掉——长弹幕显示不全；而显式宽度在
        // 测量时走 EXACTLY，不受父容器大小限制，胶囊要多宽有多宽（反正是滚动出来的）。
        tv.measure(
            android.view.View.MeasureSpec.UNSPECIFIED,
            android.view.View.MeasureSpec.UNSPECIFIED,
        )
        val rowHeight = (cfg.fontSizeSp * density * 1.35f + padV * 2 + density * 8f).toInt()
        val lp = FrameLayout.LayoutParams(
            tv.measuredWidth,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            topMargin = (cfg.topOffsetDp * density).toInt() + row * rowHeight
        }

        runCatching { root.addView(tv, lp) }.onFailure {
            // 某些 OEM（如 ColorOS）即使已授予「显示在其他应用上层」，仍会拦截后台进程绘制悬浮窗，
            // 需额外开启「后台弹出界面」权限。记录原因便于排查。
            Logger.w("danmaku addView failed: ${it.message}")
            endText(text)
            teardownOverlayIfEmpty()
            openRowEntry(row) // 失败也要放行该行，否则队列会卡死。
            return
        }

        tv.post {
            val start = root.width.toFloat().coerceAtLeast(screenWidth.toFloat())
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
                    runCatching { root.removeView(tv) }
                    endText(text)
                    teardownOverlayIfEmpty()
                }
                .start()
            // 入口占用从动画**实际开始**起算：等这条的尾部离开右缘并留出行内间距后再放行下一条。
            // （若从入队排期起算，悬浮窗首次创建/首帧布局的延迟会蚕食间距，出现首尾相贴甚至重叠。）
            val clear = entryClearMs(app, text, cfg)
            rowEstFreeAt[row] = maxOf(rowEstFreeAt[row], SystemClock.uptimeMillis() + clear)
            main.postDelayed({ openRowEntry(row) }, clear)
        }
    }

    fun overlaySettingsIntent(context: Context) =
        android.content.Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}"),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
}
