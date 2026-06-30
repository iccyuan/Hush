package com.iccyuan.hush.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.PixelFormat
import android.graphics.Path
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import com.iccyuan.hush.R
import com.iccyuan.hush.data.model.MacroStep
import com.iccyuan.hush.data.model.MacroStepType
import com.iccyuan.hush.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * 无障碍服务：既负责「回放」录制好的打卡宏（[playMacro] 用 [dispatchGesture] 模拟点击/滑动），
 * 也负责「录制」——通过一个可触摸的全屏捕获层记录用户的真实手势，并实时转发给底层 App，
 * 让用户在录制时就能看到 App 正常响应；一个可拖动的悬浮球控制开始/停止。
 */
class HushAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val wm: WindowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    private var bubble: View? = null
    private var bubbleLabel: TextView? = null
    private var captureView: View? = null
    private var capturing = false
    private var captureTimeoutJob: kotlinx.coroutines.Job? = null

    // 录制手势的临时状态。
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastEndTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Logger.i("accessibility connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* 不消费事件，仅用于手势 */ }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        teardownRecording()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardownRecording()
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    // ---------------- 回放 ----------------

    /** 回放一段宏：按每步 [MacroStep.delayMs] 间隔依次模拟手势，坐标按当前屏幕缩放。 */
    fun playMacro(steps: List<MacroStep>, recW: Int, recH: Int, repeat: Int) {
        if (steps.isEmpty()) return
        scope.launch {
            val dm = resources.displayMetrics
            val sx = if (recW > 0) dm.widthPixels.toFloat() / recW else 1f
            val sy = if (recH > 0) dm.heightPixels.toFloat() / recH else 1f
            repeat(repeat.coerceAtLeast(1)) {
                for (s in steps) {
                    if (s.delayMs > 0) delay(s.delayMs)
                    if (s.type == MacroStepType.WAIT) continue
                    val g = buildGesture(s, sx, sy) ?: continue
                    runCatching { dispatchGesture(g, null, null) }
                        .onFailure { Logger.w("dispatchGesture failed: ${it.message}") }
                    // 留出手势执行时间，避免与下一步重叠。
                    delay(s.durationMs.coerceIn(50, 3000))
                }
            }
        }
    }

    private fun buildGesture(s: MacroStep, sx: Float, sy: Float): GestureDescription? {
        val x = s.x * sx
        val y = s.y * sy
        val path = Path()
        if (s.type == MacroStepType.SWIPE) {
            path.moveTo(x, y)
            path.lineTo(s.x2 * sx, s.y2 * sy)
        } else { // TAP
            path.moveTo(x, y)
            path.lineTo(x, y)
        }
        val dur = s.durationMs.coerceIn(1, 60_000)
        return runCatching {
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, dur))
                .build()
        }.getOrNull()
    }

    // ---------------- 录制 ----------------

    /** 显示录制悬浮球（不立刻捕获）：用户先切到目标 App、定位到对应界面，再点球开始捕获。 */
    fun startRecording() {
        if (bubble != null) return
        val dm = resources.displayMetrics
        MacroRecorder.begin(dm.widthPixels, dm.heightPixels)
        showBubble()
    }

    /** 结束整轮录制：移除悬浮球与捕获层，标记完成（已录步骤保留供 UI 读取）。 */
    fun stopRecording() {
        teardownRecording()
        MacroRecorder.finish()
    }

    private fun teardownRecording() {
        stopCapture()
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        bubbleLabel = null
        if (MacroRecorder.isRecording.value) MacroRecorder.finish()
    }

    private fun showBubble() {
        val pad = (12 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.bg_macro_bubble)
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        val toggle = TextView(this).apply {
            text = getString(R.string.macro_rec_start)
            textSize = 15f
            setTextColor(0xFFFF3B30.toInt())
            setPadding(pad / 2, 0, pad, 0)
            setOnClickListener { if (!capturing) startCapture() else stopCapture() }
        }
        val done = TextView(this).apply {
            text = getString(R.string.macro_rec_done)
            textSize = 15f
            setTextColor(0xFF0A84FF.toInt())
            setPadding(pad, 0, pad / 2, 0)
            setOnClickListener { stopRecording() }
        }
        root.addView(toggle)
        root.addView(done)
        bubbleLabel = toggle
        bubble = root

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = pad
            y = (96 * resources.displayMetrics.density).toInt()
        }
        // 让悬浮球可拖动，避免挡住目标按钮。
        makeDraggable(root, lp)
        runCatching { wm.addView(root, lp) }
            .onFailure { Logger.w("add bubble failed: ${it.message}") }
    }

    private fun makeDraggable(view: View, lp: WindowManager.LayoutParams) {
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f; var moved = false
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y; touchX = e.rawX; touchY = e.rawY; moved = false; false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX); val dy = (e.rawY - touchY)
                    if (hypot(dx, dy) > 12) {
                        moved = true
                        lp.x = startX + dx.toInt(); lp.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(view, lp) }
                    }
                    moved
                }
                // 拖动时吞掉 UP，避免误触发子视图点击。
                MotionEvent.ACTION_UP -> moved
                else -> false
            }
        }
    }

    private fun startCapture() {
        if (capturing) return
        lastEndTime = 0L
        val v = View(this).apply {
            setBackgroundResource(R.drawable.bg_macro_capture) // 红框提示：正在录制
            setOnTouchListener { _, e -> onCaptureTouch(e) }
        }
        captureView = v
        // 关键安全：捕获层**不**铺满全屏，刻意空出顶部状态栏与底部「返回桌面」手势区，
        // 使用户随时能下拉通知栏 / 回到桌面——避免被全屏捕获层困住。
        val density = resources.displayMetrics.density
        val statusBar = sysDimen("status_bar_height")
        val bottomReserve = maxOf(sysDimen("navigation_bar_height"), (52 * density).toInt())
        val screenH = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.maximumWindowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION") resources.displayMetrics.heightPixels
        }
        val captureH = (screenH - statusBar - bottomReserve).coerceAtLeast((120 * density).toInt())
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            captureH,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // 不再用 FLAG_LAYOUT_IN_SCREEN / NO_LIMITS——否则会盖住系统栏与手势区、困住用户。
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // 不设 IN_SCREEN，坐标原点已在状态栏之下；顶端贴内容区，底部靠缩短高度留出手势区。
            gravity = Gravity.TOP or Gravity.START
        }
        runCatching { wm.addView(v, lp) }
            .onFailure { Logger.w("add capture failed: ${it.message}"); return }
        capturing = true
        // 安全兜底：录制最多持续 30 秒，超时自动停止，避免任何情况下被困住。
        scheduleCaptureTimeout()
        bubbleLabel?.text = getString(R.string.macro_rec_stop)
        // 把悬浮球重新加到最上层，否则会被全屏捕获层盖住。
        bubble?.let { b ->
            runCatching {
                val blp = b.layoutParams as WindowManager.LayoutParams
                wm.removeView(b); wm.addView(b, blp)
            }
        }
    }

    private fun stopCapture() {
        captureTimeoutJob?.cancel(); captureTimeoutJob = null
        captureView?.let { runCatching { wm.removeView(it) } }
        captureView = null
        if (capturing) {
            capturing = false
            bubbleLabel?.text = getString(R.string.macro_rec_start)
        }
    }

    /** 兜底：录制最多 30 秒，超时自动停止捕获，杜绝任何「被困住」的可能。 */
    private fun scheduleCaptureTimeout() {
        captureTimeoutJob?.cancel()
        captureTimeoutJob = scope.launch {
            delay(30 * 1000L)
            if (capturing) stopCapture()
        }
    }

    /** 读取系统 dimen（状态栏/导航栏高度），取不到时回退 0。 */
    private fun sysDimen(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun onCaptureTouch(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY; downTime = SystemClock.uptimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                val upX = e.rawX; val upY = e.rawY
                val now = SystemClock.uptimeMillis()
                val dt = (now - downTime).coerceAtLeast(1)
                val dist = hypot(upX - downX, upY - downY)
                val delayMs = if (lastEndTime == 0L) 0L else (downTime - lastEndTime).coerceAtLeast(0)
                val step = if (dist < TAP_SLOP_PX) {
                    MacroStep(
                        type = MacroStepType.TAP, x = downX.toInt(), y = downY.toInt(),
                        durationMs = dt.coerceIn(30, 1000), delayMs = delayMs,
                    )
                } else {
                    MacroStep(
                        type = MacroStepType.SWIPE,
                        x = downX.toInt(), y = downY.toInt(), x2 = upX.toInt(), y2 = upY.toInt(),
                        durationMs = dt.coerceIn(50, 3000), delayMs = delayMs,
                    )
                }
                MacroRecorder.addStep(step)
                lastEndTime = now
                // 实时把同一手势转发给底层 App，让用户看到 App 正常响应。
                buildGesture(step, 1f, 1f)?.let { g ->
                    runCatching { dispatchGesture(g, null, null) }
                }
            }
        }
        return true // 消费触摸，由我们转发，避免被记录两次。
    }

    private val TAP_SLOP_PX: Float
        get() = 24 * resources.displayMetrics.density

    companion object {
        /** 服务运行时的单例；为 null 表示用户尚未在系统设置中开启无障碍。 */
        @Volatile
        var instance: HushAccessibilityService? = null
            private set
    }
}
