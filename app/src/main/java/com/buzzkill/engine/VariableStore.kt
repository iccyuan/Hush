package com.buzzkill.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * 引擎在进程生命周期内的运行时状态：用户自定义变量
 *（[SetVariableAction][com.buzzkill.data.model.Action.SetVariableAction]）、按规则的
 * 冷却时间戳，以及按包名的静音时间窗口。
 *
 * 核心保持纯净、与 Android 无关（便于单元测试）。Android 层可通过 [setPersistence]
 * 注入一个持久化回调，并在启动时用 [restore] 灌入已保存的状态，从而让冷却/静音/变量
 * 在进程被系统回收或设备重启后依然有效。
 */
object VariableStore {
    private val variables = ConcurrentHashMap<String, String>()
    private val cooldownUntil = ConcurrentHashMap<Long, Long>()
    private val muteUntil = ConcurrentHashMap<String, Long>()

    /** 任一状态发生变化时触发；由 Android 层用于将快照异步落盘。 */
    @Volatile private var onChange: (() -> Unit)? = null

    fun setVariable(name: String, value: String) {
        if (name.isNotBlank()) {
            variables[name] = value
            onChange?.invoke()
        }
    }

    fun getVariable(name: String): String? = variables[name]

    fun snapshot(): Map<String, String> = variables.toMap()

    /** 若规则仍处于其冷却时间窗口内，则返回 true。 */
    fun isInCooldown(ruleId: Long, now: Long): Boolean =
        (cooldownUntil[ruleId] ?: 0L) > now

    fun startCooldown(ruleId: Long, until: Long) {
        cooldownUntil[ruleId] = until
        onChange?.invoke()
    }

    fun muteApp(pkg: String, until: Long) {
        muteUntil[pkg] = until
        onChange?.invoke()
    }

    fun isAppMuted(pkg: String, now: Long): Boolean =
        (muteUntil[pkg] ?: 0L) > now

    fun clear() {
        variables.clear()
        cooldownUntil.clear()
        muteUntil.clear()
        onChange?.invoke()
    }

    // --- 持久化支持（仅供 Android 层使用，核心逻辑不依赖于此）---

    fun cooldownsSnapshot(): Map<Long, Long> = cooldownUntil.toMap()
    fun mutesSnapshot(): Map<String, Long> = muteUntil.toMap()

    /** 在启动时用已持久化的状态填充内存（仅在尚无对应键时灌入，避免覆盖更新的值）。 */
    fun restore(
        vars: Map<String, String>,
        cooldowns: Map<Long, Long>,
        mutes: Map<String, Long>,
    ) {
        vars.forEach { (k, v) -> variables.putIfAbsent(k, v) }
        cooldowns.forEach { (k, v) -> cooldownUntil.putIfAbsent(k, v) }
        mutes.forEach { (k, v) -> muteUntil.putIfAbsent(k, v) }
    }

    /** 注册（或以 null 解除）变更回调。 */
    fun setPersistence(callback: (() -> Unit)?) {
        onChange = callback
    }
}
