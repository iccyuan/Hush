package com.iccyuan.hush.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iccyuan.hush.BuildConfig
import com.iccyuan.hush.data.DanmakuConfig
import com.iccyuan.hush.data.HolidayProvider
import com.iccyuan.hush.data.RuleRepository
import com.iccyuan.hush.data.SettingsStore
import com.iccyuan.hush.data.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RuleRepository.get(app)
    private val settings = SettingsStore.get(app)

    val masterEnabled: StateFlow<Boolean> = settings.masterEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val logActivity: StateFlow<Boolean> = settings.logActivity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val hideFromRecents: StateFlow<Boolean> = settings.hideFromRecents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val immersiveDanmaku: StateFlow<Boolean> = settings.immersiveDanmaku
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // 弹幕配置以 VM 内存状态为唯一真源：改动立即生效并异步落盘。若直接映射 DataStore 的 Flow，
    // 改一个字段后要等异步回读才更新，此时改另一个字段会基于旧值覆盖，导致「改一个回退另一个」。
    private val _danmakuConfig = MutableStateFlow(DanmakuConfig())
    val danmakuConfig: StateFlow<DanmakuConfig> = _danmakuConfig.asStateFlow()

    init {
        viewModelScope.launch { _danmakuConfig.value = settings.danmakuConfig.first() }
    }

    fun setMasterEnabled(value: Boolean) = viewModelScope.launch {
        settings.setMasterEnabled(value)
    }

    fun setLogActivity(value: Boolean) = viewModelScope.launch {
        settings.setLogActivity(value)
    }

    fun setHideFromRecents(value: Boolean) = viewModelScope.launch {
        settings.setHideFromRecents(value)
    }

    fun setImmersiveDanmaku(value: Boolean) = viewModelScope.launch {
        settings.setImmersiveDanmaku(value)
    }

    /** 立即更新内存状态与控制器，并异步落盘。 */
    fun setDanmakuConfig(c: DanmakuConfig) {
        _danmakuConfig.value = c
        com.iccyuan.hush.service.DanmakuController.updateConfig(c)
        viewModelScope.launch { settings.setDanmakuConfig(c) }
    }

    /** 用当前配置弹一条示例弹幕以供预览（需悬浮窗权限）。 */
    fun previewDanmaku() {
        val ctx = getApplication<Application>()
        com.iccyuan.hush.service.DanmakuController.updateConfig(danmakuConfig.value)
        com.iccyuan.hush.service.DanmakuController.show(
            ctx, ctx.getString(com.iccyuan.hush.R.string.danmaku_preview_sample), force = true,
        )
    }

    fun exportRules(onResult: (String) -> Unit) = viewModelScope.launch {
        onResult(repository.exportJson())
    }

    fun importRules(json: String, onResult: (Int) -> Unit) = viewModelScope.launch {
        val count = runCatching { repository.importJson(json) }.getOrDefault(-1)
        onResult(count)
    }

    /** 节假日数据上次更新的时间（epoch 毫秒），从未更新则为 0。 */
    val holidayUpdated = MutableStateFlow(HolidayProvider.lastUpdated(getApplication()))
    val holidayUpdating = MutableStateFlow(false)

    fun updateHolidays(onResult: (Boolean) -> Unit) = viewModelScope.launch {
        holidayUpdating.value = true
        val result = withContext(Dispatchers.IO) { HolidayProvider.refresh(getApplication()) }
        holidayUpdating.value = false
        holidayUpdated.value = HolidayProvider.lastUpdated(getApplication())
        onResult(result.ok)
    }

    /** 用于和 GitHub 最新版比较的纯版本号（如 0.1.6）。 */
    val appVersion: String = BuildConfig.VERSION_NAME

    /** 关于页展示用的完整版本信息：版本名 + 构建号（如 0.1.6 (6)）。 */
    val appVersionDisplay: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    val updateChecking = MutableStateFlow(false)

    /** 检查 GitHub 是否有新版本。结果为 null 表示检查失败（网络等原因）。 */
    fun checkUpdate(onResult: (UpdateChecker.Result?) -> Unit) = viewModelScope.launch {
        updateChecking.value = true
        val result = withContext(Dispatchers.IO) { UpdateChecker.check(appVersion) }
        updateChecking.value = false
        onResult(result)
    }
}
