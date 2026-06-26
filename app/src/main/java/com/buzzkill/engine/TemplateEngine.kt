package com.buzzkill.engine

import com.buzzkill.data.model.NotificationField

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

    fun render(template: String, ctx: MatchContext): String {
        if (template.isEmpty()) return template
        return TOKEN.replace(template) { m ->
            val key = m.groupValues[1]
            resolve(key, ctx) ?: m.value
        }
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
        key.toIntOrNull() != null -> ctx.captures[key] ?: ""
        else -> null
    }
}
