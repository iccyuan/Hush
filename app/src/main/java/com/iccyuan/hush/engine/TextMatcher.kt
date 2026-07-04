package com.iccyuan.hush.engine

import com.iccyuan.hush.data.model.MatchMode
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/** 文本比较的结果，携带任意正则捕获组。 */
data class MatchResult(val matched: Boolean, val groups: Map<String, String> = emptyMap())

/** 由触发器和替换动作共享的无状态比较辅助方法。 */
object TextMatcher {

    // 按 (pattern, flags) 缓存编译结果，避免每条通知都重新编译同一条规则的正则。
    // 规则数量有限，缓存无需驱逐；模式文本变化会自然产生新键，编辑规则后旧模式的正则不会再被使用。
    private val patternCache = ConcurrentHashMap<Pair<String, Int>, Pattern>()

    fun evaluate(
        mode: MatchMode,
        query: String,
        value: String,
        caseSensitive: Boolean,
    ): MatchResult {
        val hay = if (caseSensitive) value else value.lowercase()
        val needle = if (caseSensitive) query else query.lowercase()
        return when (mode) {
            MatchMode.CONTAINS -> MatchResult(needle.isNotEmpty() && hay.contains(needle))
            MatchMode.EQUALS -> MatchResult(hay == needle)
            MatchMode.STARTS_WITH -> MatchResult(hay.startsWith(needle))
            MatchMode.ENDS_WITH -> MatchResult(hay.endsWith(needle))
            MatchMode.REGEX -> regex(query, value, caseSensitive)
            MatchMode.WILDCARD -> regex(wildcardToRegex(query), value, caseSensitive)
        }
    }

    private fun regex(pattern: String, value: String, caseSensitive: Boolean): MatchResult {
        return try {
            val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
            val compiled = patternCache.getOrPut(pattern to flags) { Pattern.compile(pattern, flags) }
            val m = compiled.matcher(value)
            if (m.find()) {
                val groups = buildMap {
                    for (i in 1..m.groupCount()) {
                        put(i.toString(), m.group(i) ?: "")
                    }
                }
                MatchResult(true, groups)
            } else {
                MatchResult(false)
            }
        } catch (_: Exception) {
            // 无效的模式只会永不匹配，而不会导致监听器崩溃。
            MatchResult(false)
        }
    }

    /**
     * 校验正则表达式：合法返回 null，非法返回平台给出的错误信息（供编辑器内联提示）。
     * 与匹配热路径中“非法即永不匹配”的容错策略相互独立。
     */
    fun regexError(pattern: String): String? =
        try {
            Pattern.compile(pattern)
            null
        } catch (e: Exception) {
            e.message ?: "Invalid regular expression"
        }

    /** 将通配符（`*`、`?`）转换为带锚点的正则表达式。 */
    private fun wildcardToRegex(glob: String): String {
        val sb = StringBuilder("^")
        for (c in glob) {
            when (c) {
                '*' -> sb.append(".*")
                '?' -> sb.append('.')
                else -> sb.append(Pattern.quote(c.toString()))
            }
        }
        return sb.append('$').toString()
    }
}
