package com.iccyuan.hush.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.iccyuan.hush.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 下拉通知栏的快捷设置磁贴：一键开关全局总开关（[SettingsStore.masterEnabled]）。
 */
class MasterToggleTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { render(store().masterEnabled.first()) }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val store = store()
            val next = !store.masterEnabled.first()
            store.setMasterEnabled(next)
            render(next)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun store() = SettingsStore.get(this)

    private fun render(enabled: Boolean) {
        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }
    }
}
