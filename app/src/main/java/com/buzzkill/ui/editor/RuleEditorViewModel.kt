package com.buzzkill.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.buzzkill.data.RuleRepository
import com.buzzkill.data.model.Action
import com.buzzkill.data.model.Condition
import com.buzzkill.data.model.LogicMode
import com.buzzkill.data.model.Rule
import com.buzzkill.data.model.Trigger
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
    fun addCondition(condition: Condition) = update { it.copy(conditions = it.conditions + condition) }
    fun updateCondition(condition: Condition) = update {
        it.copy(conditions = it.conditions.map { c -> if (c.id == condition.id) condition else c })
    }
    fun removeCondition(id: String) = update {
        it.copy(conditions = it.conditions.filterNot { c -> c.id == id })
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
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val r = _rule.value
            if (r.id != 0L) repository.delete(r)
            onDone()
        }
    }
}
