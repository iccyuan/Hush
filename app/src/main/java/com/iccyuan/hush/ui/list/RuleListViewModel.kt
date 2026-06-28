package com.iccyuan.hush.ui.list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iccyuan.hush.data.RuleRepository
import com.iccyuan.hush.data.SettingsStore
import com.iccyuan.hush.data.model.Rule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RuleListViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RuleRepository.get(app)
    private val settings = SettingsStore.get(app)

    val rules: StateFlow<List<Rule>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val masterEnabled: StateFlow<Boolean> = settings.masterEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setEnabled(rule: Rule, enabled: Boolean) = viewModelScope.launch {
        repository.setEnabled(rule.id, enabled)
        // 停用规则即解除它设置的应用静音。
        if (!enabled) com.iccyuan.hush.engine.VariableStore.unmuteByRule(rule.id)
    }

    fun setMasterEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setMasterEnabled(enabled)
    }

    fun delete(rule: Rule) = viewModelScope.launch {
        repository.delete(rule)
        com.iccyuan.hush.engine.VariableStore.unmuteByRule(rule.id)
    }

    fun duplicate(rule: Rule) = viewModelScope.launch {
        repository.upsert(rule.copy(id = 0, name = "${rule.name} (copy)"))
    }
}
