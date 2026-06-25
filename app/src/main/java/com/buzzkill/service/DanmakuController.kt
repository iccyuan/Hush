package com.buzzkill.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * Shows scrolling "danmaku" bullet text across the top of the screen via an overlay
 * window. Each call adds one bullet that animates from the right edge to off the left,
 * then removes itself. Bullets are placed on a few rotating rows so they don't overlap.
 * Requires the draw-over-other-apps permission.
 */
object DanmakuController {

    private val main = Handler(Looper.getMainLooper())
    private var rowIndex = 0
    private const val ROWS = 4

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(context: Context, text: String, durationMs: Long) {
        if (text.isBlank() || !canShow(context)) return
        val app = context.applicationContext
        main.post { showOnMain(app, text, durationMs) }
    }

    private fun showOnMain(app: Context, text: String, durationMs: Long) {
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val metrics = app.resources.displayMetrics
        val screenWidth = metrics.widthPixels

        val tv = TextView(app).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 17f
            setSingleLine()
            setShadowLayer(8f, 0f, 0f, Color.argb(180, 0, 0, 0))
            setPadding(24, 8, 24, 8)
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
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
            val topInset = (metrics.density * 48).toInt()
            val rowHeight = (metrics.density * 40).toInt()
            y = topInset + (rowIndex % ROWS) * rowHeight
        }
        rowIndex = (rowIndex + 1) % ROWS

        runCatching { wm.addView(tv, lp) } .onFailure { return }

        // Start off the right edge, then translate left until fully off-screen.
        tv.translationX = screenWidth.toFloat()
        tv.post {
            tv.animate()
                .translationX(-tv.width.toFloat())
                .setDuration(durationMs.coerceIn(2000, 20000))
                .withEndAction { runCatching { wm.removeView(tv) } }
                .start()
        }
    }

    fun overlaySettingsIntent(context: Context) =
        android.content.Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}"),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
}
