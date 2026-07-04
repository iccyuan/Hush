package com.iccyuan.hush.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iccyuan.hush.data.NotificationLogRepository
import com.iccyuan.hush.data.RuleRepository
import com.iccyuan.hush.data.db.AppCount
import com.iccyuan.hush.data.model.MatchMode
import com.iccyuan.hush.data.model.NotificationField
import com.iccyuan.hush.data.model.NotificationLog
import com.iccyuan.hush.data.model.Rule
import com.iccyuan.hush.data.model.Trigger
import com.iccyuan.hush.ui.Ids
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = NotificationLogRepository.get(app)
    private val rules = RuleRepository.get(app)

    private val _selectedApp = MutableStateFlow<String?>(null)
    val selectedApp: StateFlow<String?> = _selectedApp.asStateFlow()

    // 当前加载条数上限；上拉加载更多即增大它（响应式查询据此扩窗）。
    private val _limit = MutableStateFlow(PAGE)

    /** 分页后的历史列表：随所选应用与加载上限变化，且新通知到达时由 Room 自动刷新。 */
    val logs: StateFlow<List<NotificationLog>> =
        combine(_selectedApp, _limit) { pkg, n -> pkg to n }
            .flatMapLatest { (pkg, n) -> repo.observePage(pkg, n) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 统计用时间戳（全部，上限 MAX_ROWS），与列表分页解耦，使统计始终基于完整数据。 */
    val times: StateFlow<List<Long>> =
        _selectedApp.flatMapLatest { pkg -> repo.observeTimes(pkg) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 过滤行的应用聚合（响应式，从多到少）。 */
    val appCounts: StateFlow<List<AppCount>> = repo.observeAppCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 是否还有更多可加载：已加载数小于（过滤后的）总数。 */
    val canLoadMore: StateFlow<Boolean> =
        combine(logs, times) { l, t -> l.size < t.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 规则 id → 名称，用于在历史里展示「被哪条规则命中」。 */
    val ruleNames: StateFlow<Map<Long, String>> = rules.observeAll()
        .map { list -> list.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setSelectedApp(pkg: String?) {
        _selectedApp.value = pkg
        _limit.value = PAGE // 切换应用时重置分页，从头开始
    }

    /** 上拉加载下一批（增大 LIMIT）。到达总数或上限则不再增长。 */
    fun loadMore() {
        if (!canLoadMore.value) return
        _limit.value = (_limit.value + PAGE).coerceAtMost(NotificationLogRepository.MAX_ROWS)
    }

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

    companion object {
        private const val PAGE = 50
    }
}
