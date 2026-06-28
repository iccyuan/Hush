package com.iccyuan.hush.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iccyuan.hush.data.NotificationLogRepository
import com.iccyuan.hush.data.RuleRepository
import com.iccyuan.hush.data.model.MatchMode
import com.iccyuan.hush.data.model.NotificationField
import com.iccyuan.hush.data.model.NotificationLog
import com.iccyuan.hush.data.model.Rule
import com.iccyuan.hush.data.model.Trigger
import com.iccyuan.hush.ui.Ids
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = NotificationLogRepository.get(app)
    private val rules = RuleRepository.get(app)

    val logs: StateFlow<List<NotificationLog>> = repo.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 规则 id → 名称，用于在历史里展示「被哪条规则命中」。 */
    val ruleNames: StateFlow<Map<Long, String>> = rules.observeAll()
        .map { list -> list.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun clear() = viewModelScope.launch { repo.clear() }
    fun delete(log: NotificationLog) = viewModelScope.launch { repo.deleteById(log.id) }

    /**
     * 根据一条历史通知预填一条新规则（限定该应用 + 一个文本触发器），插入后回调其 id，
     * 便于上层直接打开编辑器继续完善。
     */
    fun createRuleFrom(log: NotificationLog, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val query = log.text.ifBlank { log.title }
        val field = if (log.text.isNotBlank()) NotificationField.TEXT else NotificationField.TITLE
        val triggers = if (query.isBlank()) emptyList()
            else listOf(Trigger.TextTrigger(Ids.next(), field, MatchMode.CONTAINS, query.take(80)))
        val id = rules.upsert(
            Rule(
                name = log.appName.ifBlank { log.packageName },
                appPackages = listOf(log.packageName),
                triggers = triggers,
            )
        )
        onCreated(id)
    }
}
