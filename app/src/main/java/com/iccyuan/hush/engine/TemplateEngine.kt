package com.iccyuan.hush.engine

import com.iccyuan.hush.data.model.NotificationField
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 针对 [MatchContext] 渲染用户提供的模板。
 *
 * 支持的占位符：
 *  - {title} {text} {bigtext} {subtext} {ticker} {app} {package}
 *  - {1}..{9}            来自匹配触发器的正则捕获组
 *  - {var:name}          由 SetVariableAction 设置的用户变量
 *  - {time} {date}       不涉及复杂时区处理；仅为简单的格式化值
 *
 * 未知的占位符将保持原样，以便字面意义的花括号得以保留。
 */
object TemplateEngine {

    // 注意：右花括号经过转义——与桌面 JVM 不同，Android 的 ICU 正则引擎
    // 会拒绝出现在 {n,m} 量词之外的裸 '}'。
    private val TOKEN = Regex("""\{([a-zA-Z0-9_:]+)\}""")

    /**
     * 渲染模板。[escape] 仅作用于「被替换进来的占位符值」，不影响模板里用户写的字面字符——
     * 用于 JSON 请求体时传入 [jsonEscape]，使 {text} 等值里的引号/换行等被转义，不破坏 JSON。
     */
    fun render(template: String, ctx: MatchContext, escape: (String) -> String = { it }): String {
        if (template.isEmpty()) return template
        return TOKEN.replace(template) { m ->
            val key = m.groupValues[1]
            resolve(key, ctx)?.let(escape) ?: m.value
        }
    }

    /** 把字符串转义成可安全放进 JSON 字符串字面量内部的形式（不含外层引号）。 */
    fun jsonEscape(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun resolve(key: String, ctx: MatchContext): String? = when {
        key.startsWith("var:") -> VariableStore.getVariable(key.removePrefix("var:")) ?: ""
        key == "title" -> ctx.field(NotificationField.TITLE)
        key == "text" -> ctx.field(NotificationField.TEXT)
        key == "bigtext" -> ctx.field(NotificationField.BIG_TEXT)
        key == "subtext" -> ctx.field(NotificationField.SUB_TEXT)
        key == "ticker" -> ctx.field(NotificationField.TICKER)
        key == "app" -> ctx.appName
        key == "package" -> ctx.packageName
        key == "time" -> formatNow(TIME_FMT, ctx)
        key == "date" -> formatNow(DATE_FMT, ctx)
        key.toIntOrNull() != null -> ctx.captures[key] ?: ""
        else -> null
    }

    // 以采样的设备时间（device.nowMillis）渲染。当 now 为 0（仅内容预览的中性
    // 设备状态）时，回退到当前挂钟时间，以免预览出现 1970 年。
    // DateTimeFormatter 不可变、线程安全，可放心在 onNotificationPosted 的多线程协程间共享
    // ——不同于 SimpleDateFormat（此前的实现在并发渲染 {time}/{date} 时会有状态错乱风险）。
    private fun formatNow(fmt: DateTimeFormatter, ctx: MatchContext): String {
        val millis = ctx.device.nowMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        return fmt.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    }

    private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
}
