package com.buzzkill.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * 引擎在进程生命周期内的运行时状态：用户自定义变量
 *（[SetVariableAction][com.buzzkill.data.model.Action.SetVariableAction]）、按规则的
 * 冷却时间戳，以及按包名的静音时间窗口。刻意保存在内存中——
 * 这些都是临时的自动化状态，并非需要持久化的用户数据。
 */
object VariableStore {
    private val variables = ConcurrentHashMap<String, String>()
    private val cooldownUntil = ConcurrentHashMap<Long, Long>()
    private val muteUntil = ConcurrentHashMap<String, Long>()

    fun setVariable(name: String, value: String) {
        if (name.isNotBlank()) variables[name] = value
    }

    fun getVariable(name: String): String? = variables[name]

    fun snapshot(): Map<String, String> = variables.toMap()

    /** 若规则仍处于其冷却时间窗口内，则返回 true。 */
    fun isInCooldown(ruleId: Long, now: Long): Boolean =
        (cooldownUntil[ruleId] ?: 0L) > now

    fun startCooldown(ruleId: Long, until: Long) {
        cooldownUntil[ruleId] = until
    }

    fun muteApp(pkg: String, until: Long) {
        muteUntil[pkg] = until
    }

    fun isAppMuted(pkg: String, now: Long): Boolean =
        (muteUntil[pkg] ?: 0L) > now

    fun clear() {
        variables.clear()
        cooldownUntil.clear()
        muteUntil.clear()
    }
}
