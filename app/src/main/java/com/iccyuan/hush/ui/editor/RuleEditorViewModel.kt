package com.iccyuan.hush.ui.editor
import com.iccyuan.hush.engine.VariableStore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iccyuan.hush.data.RuleRepository
import com.iccyuan.hush.data.model.Action
import com.iccyuan.hush.data.model.Condition
import com.iccyuan.hush.data.model.GapOp
import com.iccyuan.hush.data.model.LogicMode
import com.iccyuan.hush.data.model.Rule
import com.iccyuan.hush.data.model.Trigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RuleEditorViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RuleRepository.get(app)

    private val _rule = MutableStateFlow(Rule())
    val rule: StateFlow<Rule> = _rule.asStateFlow()

    private var loaded = false

    fun load(ruleId: Long) {
        if (loaded) return
        loaded = true
        if (ruleId == 0L) return
        viewModelScope.launch {
            repository.byId(ruleId)?.let { _rule.value = it }
        }
    }

    private fun update(transform: (Rule) -> Rule) {
        _rule.value = transform(_rule.value)
    }

    fun setName(name: String) = update { it.copy(name = name) }
    fun setNotes(notes: String) = update { it.copy(notes = notes) }
    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }
    fun setStopProcessing(stop: Boolean) = update { it.copy(stopProcessing = stop) }
    fun setShowDanmaku(show: Boolean) = update { it.copy(showDanmaku = show) }
    fun setTriggerLogic(logic: LogicMode) = update { it.copy(triggerLogic = logic) }
    fun setApps(packages: List<String>) = update { it.copy(appPackages = packages) }

    // --- 触发器 ---
    fun addTrigger(trigger: Trigger) = update { it.copy(triggers = it.triggers + trigger) }
    fun updateTrigger(trigger: Trigger) = update {
        it.copy(triggers = it.triggers.map { t -> if (t.id == trigger.id) trigger else t })
    }
    fun removeTrigger(id: String) = update {
        it.copy(triggers = it.triggers.filterNot { t -> t.id == id })
    }

    // --- 条件 ---
    fun addCondition(condition: Condition) = update { r ->
        val newConds = r.conditions + condition
        // 新增条件时为新间隔补一个默认连接符：时间段/节假日之间默认「或」，其余默认「且」。
        val newJoins = if (r.conditions.isEmpty()) r.conditionJoins
        else r.conditionJoins + defaultGap(r.conditions.last(), condition)
        r.copy(conditions = newConds, conditionJoins = newJoins)
    }

    fun updateCondition(condition: Condition) = update {
        it.copy(conditions = it.conditions.map { c -> if (c.id == condition.id) condition else c })
    }

    fun removeCondition(id: String) = update { r ->
        val idx = r.conditions.indexOfFirst { it.id == id }
        if (idx < 0) return@update r
        val newConds = r.conditions.filterIndexed { i, _ -> i != idx }
        // 删一个条件就删去与之相邻的一个间隔：非首个删它前面的间隔，首个删其后的间隔。
        val gapToRemove = if (idx > 0) idx - 1 else 0
        val newJoins =
            if (r.conditionJoins.isEmpty()) r.conditionJoins
            else r.conditionJoins.filterIndexed { i, _ -> i != gapToRemove }
        r.copy(conditions = newConds, conditionJoins = newJoins)
    }

    /** 设置第 [index] 个间隔（conditions[index] 与 conditions[index+1] 之间）的连接符。 */
    fun setGap(index: Int, op: GapOp) = update { r ->
        if (index !in r.conditionJoins.indices) r
        else r.copy(conditionJoins = r.conditionJoins.mapIndexed { i, g -> if (i == index) op else g })
    }

    private fun defaultGap(a: Condition, b: Condition): GapOp {
        fun temporal(c: Condition) = c is Condition.TimeCondition || c is Condition.HolidayCondition
        return if (temporal(a) && temporal(b)) GapOp.OR else GapOp.AND
    }

    // --- 动作 ---
    fun addAction(action: Action) = update { it.copy(actions = it.actions + action) }
    fun updateAction(action: Action) = update {
        it.copy(actions = it.actions.map { a -> if (a.id == action.id) action else a })
    }
    fun removeAction(id: String) = update {
        it.copy(actions = it.actions.filterNot { a -> a.id == id })
    }
    fun moveAction(from: Int, to: Int) = update { rule ->
        val list = rule.actions.toMutableList()
        if (from in list.indices && to in list.indices) {
            list.add(to, list.removeAt(from))
        }
        rule.copy(actions = list)
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val r = _rule.value
            val named = if (r.name.isBlank()) r.copy(name = "Untitled rule") else r
            repository.upsert(named)
            // 若该规则被停用，解除它设置的应用静音。
            if (!named.enabled && named.id != 0L) {
                VariableStore.unmuteByRule(named.id)
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val r = _rule.value
            if (r.id != 0L) {
                repository.delete(r)
                VariableStore.unmuteByRule(r.id)
            }
            onDone()
        }
    }
}
