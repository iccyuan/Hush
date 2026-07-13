package com.iccyuan.hush.ui.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iccyuan.hush.data.NotificationLogRepository
import com.iccyuan.hush.data.RuleRepository
import com.iccyuan.hush.data.db.AppCount
import com.iccyuan.hush.data.model.Rule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InsightsViewModel(app: Application) : AndroidViewModel(app) {

    data class State(
        val total: Int = 0,
        val matched: Int = 0,
        val topApps: List<AppCount> = emptyList(),
        val topRules: List<Rule> = emptyList(),
    )

    private val logRepo = NotificationLogRepository.get(app)
    private val ruleRepo = RuleRepository.get(app)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val rules = ruleRepo.allOnce().filter { it.fireCount > 0 }.sortedByDescending { it.fireCount }.take(8)
        _state.value = State(
            total = logRepo.total(),
            matched = logRepo.matched(),
            topApps = logRepo.topApps(),
            topRules = rules,
        )
    }

    /** 清空统计：删除通知记录（总数/最吵应用的数据源），并清零所有规则的触发计数。 */
    fun clearStats() = viewModelScope.launch {
        logRepo.clear()
        ruleRepo.clearFireCounts()
        refresh()
    }
}
