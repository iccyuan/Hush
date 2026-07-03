package com.iccyuan.hush.data

/**
 * 弹幕的全局外观与行为配置（所有弹幕共用）。由 [SettingsStore] 持久化，服务在设置变化时
 * 推给 [com.iccyuan.hush.service.DanmakuController]。
 */
data class DanmakuConfig(
    /** 文字字号（sp）。 */
    val fontSizeSp: Float = 20f,
    /** 文字颜色（ARGB）。 */
    val color: Int = 0xFFFFFFFF.toInt(),
    /** 背景胶囊不透明度（0–255）。 */
    val bgAlpha: Int = 180,
    /** 单条弹幕从最右滑到最左的时长（毫秒）——越小越快。 */
    val durationMs: Long = 4000L,
    /** 同时可显示的行数。 */
    val rows: Int = 3,
    /** 顶部起始偏移（dp），用于避开状态栏 / 刘海。 */
    val topOffsetDp: Float = 44f,
) {
    companion object {
        // 供设置界面用的离散档位。
        val SIZES = listOf(16f, 20f, 26f)                 // 小 / 中 / 大
        val SPEEDS = listOf(6500L, 4000L, 2500L)          // 慢 / 中 / 快
        val ROWS_OPTIONS = listOf(2, 3, 4, 5)
        val COLORS = listOf(
            0xFFFFFFFF.toInt(), // 白
            0xFFFFD60A.toInt(), // 黄
            0xFF32D74B.toInt(), // 绿
            0xFF64D2FF.toInt(), // 青
            0xFFFF7AB6.toInt(), // 粉
            0xFFFF9F0A.toInt(), // 橙
        )
    }
}
