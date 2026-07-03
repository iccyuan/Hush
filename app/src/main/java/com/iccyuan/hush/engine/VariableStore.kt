package com.iccyuan.hush.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * 引擎在进程生命周期内的运行时状态：用户自定义变量
 *（[SetVariableAction][com.iccyuan.hush.data.model.Action.SetVariableAction]）、按规则的
 * 冷却时间戳，以及按包名的静音时间窗口。
 *
 * 核心保持纯净、与 Android 无关（便于单元测试）。Android 层可通过 [setPersistence]
 * 注入一个持久化回调，并在启动时用 [restore] 灌入已保存的状态，从而让冷却/静音/变量
 * 在进程被系统回收或设备重启后依然有效。
 */
object VariableStore {
    private val variables = ConcurrentHashMap<String, String>()
    private val cooldownUntil = ConcurrentHashMap<Long, Long>()
    // 被静音的应用：包名 -> 设置该静音的规则 id。无限期生效，直到拥有它的规则被
    // 停用/删除（或总开关关闭）时自动解除——无需单独的“已静音”管理界面。
    private val mutedBy = ConcurrentHashMap<String, Long>()

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

    /** 无限期静音 [pkg]，并记录设置它的规则 [ruleId]（用于规则停用时自动解除）。 */
    fun muteApp(pkg: String, ruleId: Long) {
        mutedBy[pkg] = ruleId
        onChange?.invoke()
    }

    fun isAppMuted(pkg: String): Boolean = mutedBy.containsKey(pkg)

    /** 设置该应用静音的规则 id；未被静音则返回 null。 */
    fun mutedRuleId(pkg: String): Long? = mutedBy[pkg]

    /** 解除单个应用的静音（用于其静音规则的条件此刻不再成立时）。 */
    fun unmuteApp(pkg: String) {
        if (mutedBy.remove(pkg) != null) onChange?.invoke()
    }

    /** 解除由指定规则设置的所有静音（规则被停用/删除时调用）。 */
    fun unmuteByRule(ruleId: Long) {
        // 可能有多个包由同一规则静音；一次性移除并在确有变化时触发持久化。
        if (mutedBy.entries.removeAll { it.value == ruleId }) {
            onChange?.invoke()
        }
    }

    /** 解除全部静音（总开关关闭时调用）。 */
    fun unmuteAll() {
        if (mutedBy.isNotEmpty()) {
            mutedBy.clear()
            onChange?.invoke()
        }
    }

    fun clear() {
        variables.clear()
        cooldownUntil.clear()
        mutedBy.clear()
        onChange?.invoke()
    }

    // --- 持久化支持（仅供 Android 层使用，核心逻辑不依赖于此）---

    fun cooldownsSnapshot(): Map<Long, Long> = cooldownUntil.toMap()
    /** 包名 -> 拥有该静音的规则 id。 */
    fun mutesSnapshot(): Map<String, Long> = mutedBy.toMap()

    /** 在启动时用已持久化的状态填充内存（仅在尚无对应键时灌入，避免覆盖更新的值）。 */
    fun restore(
        vars: Map<String, String>,
        cooldowns: Map<Long, Long>,
        mutes: Map<String, Long>,
    ) {
        vars.forEach { (k, v) -> variables.putIfAbsent(k, v) }
        cooldowns.forEach { (k, v) -> cooldownUntil.putIfAbsent(k, v) }
        mutes.forEach { (k, v) -> mutedBy.putIfAbsent(k, v) }
    }

    /** 注册（或以 null 解除）变更回调。 */
    fun setPersistence(callback: (() -> Unit)?) {
        onChange = callback
    }
}
