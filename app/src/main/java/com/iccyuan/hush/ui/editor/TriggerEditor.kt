package com.iccyuan.hush.ui.editor
import com.iccyuan.hush.engine.TextMatcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.iccyuan.hush.ui.findActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import com.iccyuan.hush.ui.common.IntField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iccyuan.hush.R
import com.iccyuan.hush.data.model.DeviceEventType
import com.iccyuan.hush.data.model.MatchMode
import com.iccyuan.hush.data.model.NotificationField
import com.iccyuan.hush.data.model.Trigger
import com.iccyuan.hush.ui.Localize
import com.iccyuan.hush.ui.common.EnumDropdown
import com.iccyuan.hush.ui.common.LabeledTextField
import com.iccyuan.hush.ui.common.SwitchRow
import com.iccyuan.hush.ui.components.DialogActions
import com.iccyuan.hush.ui.components.GlassDialog
import androidx.compose.material3.MaterialTheme

@Composable
fun TriggerEditorDialog(
    existing: Trigger?,
    onSave: (Trigger) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    if (existing == null) return
    var draft by remember(existing.id) { mutableStateOf(existing) }

    GlassDialog(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.edit_trigger),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Column {
            when (val t = draft) {
                is Trigger.TextTrigger -> TextTriggerFields(t) { draft = it }
                is Trigger.OngoingTrigger -> SwitchRow(
                    stringResource(R.string.must_be_ongoing), t.mustBeOngoing
                ) { draft = t.copy(mustBeOngoing = it) }
                is Trigger.HasReplyTrigger -> SwitchRow(
                    stringResource(R.string.must_have_reply), t.mustHaveReply
                ) { draft = t.copy(mustHaveReply = it) }
                is Trigger.DeviceEvent -> DeviceEventFields(t) { draft = it }
                is Trigger.LocationTrigger -> LocationTriggerFields(t) { draft = it }
            }
        }
        DialogActions(
            confirmText = stringResource(R.string.save),
            onConfirm = { onSave(draft) },
            secondaryText = stringResource(if (onDelete != null) R.string.delete else R.string.cancel),
            onSecondary = { onDelete?.invoke() ?: onDismiss() },
        )
    }
}

@Composable
private fun LocationTriggerFields(
    t: Trigger.LocationTrigger,
    onChange: (Trigger.LocationTrigger) -> Unit,
) {
    val labels = com.iccyuan.hush.data.model.LocationEventType.entries.associateWith {
        stringResource(
            when (it) {
                com.iccyuan.hush.data.model.LocationEventType.ENTER -> R.string.loc_event_enter
                com.iccyuan.hush.data.model.LocationEventType.EXIT -> R.string.loc_event_exit
            }
        )
    }
    Column {
        Text(
            stringResource(R.string.location_trigger_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        EnumDropdown(
            label = stringResource(R.string.location_event),
            options = com.iccyuan.hush.data.model.LocationEventType.entries,
            selected = t.event,
            optionLabel = { labels.getValue(it) },
            onSelected = { onChange(t.copy(event = it)) },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (t.latitude != 0.0 || t.longitude != 0.0)
                "%.5f, %.5f".format(t.latitude, t.longitude)
            else stringResource(R.string.location_pick_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            LocationMap(
                lat = t.latitude,
                lng = t.longitude,
                radiusMeters = t.radiusMeters,
                onPick = { lat, lng ->
                    onChange(t.copy(latitude = lat, longitude = lng, placeName = "%.5f, %.5f".format(lat, lng)))
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        IntField(stringResource(R.string.location_radius_m), t.radiusMeters) {
            onChange(t.copy(radiusMeters = it.coerceIn(50, 5000)))
        }
    }
}

@Composable
private fun DeviceEventFields(t: Trigger.DeviceEvent, onChange: (Trigger.DeviceEvent) -> Unit) {
    val labels = DeviceEventType.entries.associateWith { stringResource(Localize.eventRes(it)) }
    val context = androidx.compose.ui.platform.LocalContext.current
    Column {
        Text(
            stringResource(R.string.device_event_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        EnumDropdown(
            label = stringResource(R.string.event_type),
            options = DeviceEventType.entries,
            selected = t.event,
            optionLabel = { labels.getValue(it) },
            onSelected = { onChange(t.copy(event = it)) },
        )
        // 仅 Wi-Fi 事件可限定 SSID（多选）；为空=任意 Wi-Fi。
        val isWifi = t.event == DeviceEventType.WIFI_CONNECTED || t.event == DeviceEventType.WIFI_DISCONNECTED
        if (isWifi) {
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.wifi_ssid_label), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.wifi_ssid_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (t.ssids.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SsidChips(t.ssids) { removed -> onChange(t.copy(ssids = t.ssids - removed)) }
            }
            Spacer(Modifier.height(8.dp))
            var input by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            val addTyped = {
                val s = input.trim()
                if (s.isNotEmpty() && s !in t.ssids) onChange(t.copy(ssids = t.ssids + s))
                input = ""
            }
            com.iccyuan.hush.ui.common.LabeledTextField(
                label = stringResource(R.string.wifi_ssid_input_hint),
                value = input,
                onValueChange = { input = it },
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                com.iccyuan.hush.ui.components.IOSTintedButton(
                    text = stringResource(R.string.add),
                    onClick = addTyped,
                    modifier = Modifier.weight(1f),
                )
                com.iccyuan.hush.ui.components.IOSTintedButton(
                    text = stringResource(R.string.wifi_ssid_add_current),
                    onClick = {
                        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            currentWifiSsid(context)?.let { s -> if (s !in t.ssids) onChange(t.copy(ssids = t.ssids + s)) }
                        } else {
                            // GlassDialog 内取不到 ActivityResult 注册器，改用经典的 Activity 申请（与地图选点一致）；
                            // 授予后再点一次即可添加当前 Wi-Fi。
                            context.findActivity()?.let { act ->
                                androidx.core.app.ActivityCompat.requestPermissions(
                                    act, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 0,
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 当前连接的 Wi-Fi SSID（需定位权限 + 定位开启，否则返回 null）。 */
private fun currentWifiSsid(context: android.content.Context): String? = runCatching {
    val wifi = context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java) ?: return null
    @Suppress("DEPRECATION")
    val raw = wifi.connectionInfo?.ssid ?: return null
    raw.trim('"').takeIf { it.isNotBlank() && it != "<unknown ssid>" && !it.startsWith("0x") }
}.getOrNull()

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@androidx.compose.runtime.Composable
private fun SsidChips(ssids: List<String>, onRemove: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ssids.forEach { ssid ->
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .clickable { onRemove(ssid) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(ssid, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Filled.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun TextTriggerFields(t: Trigger.TextTrigger, onChange: (Trigger.TextTrigger) -> Unit) {
    val fieldLabels = NotificationField.entries.associateWith { stringResource(Localize.fieldRes(it)) }
    val modeLabels = MatchMode.entries.associateWith { stringResource(Localize.matchRes(it)) }
    Column {
        EnumDropdown(
            label = stringResource(R.string.field),
            options = NotificationField.entries,
            selected = t.field,
            optionLabel = { fieldLabels.getValue(it) },
            onSelected = { onChange(t.copy(field = it)) },
        )
        Spacer(Modifier.height(8.dp))
        EnumDropdown(
            label = stringResource(R.string.match),
            options = MatchMode.entries,
            selected = t.mode,
            optionLabel = { modeLabels.getValue(it) },
            onSelected = { onChange(t.copy(mode = it)) },
        )
        Spacer(Modifier.height(8.dp))
        val queryError = if (t.mode == MatchMode.REGEX) {
            TextMatcher.regexError(t.query)
                ?.let { stringResource(R.string.err_invalid_regex, it) }
        } else null
        LabeledTextField(stringResource(R.string.query), t.query, error = queryError) {
            onChange(t.copy(query = it))
        }
        SwitchRow(stringResource(R.string.case_sensitive), t.caseSensitive) {
            onChange(t.copy(caseSensitive = it))
        }
        SwitchRow(stringResource(R.string.negate), t.negate) { onChange(t.copy(negate = it)) }
    }
}
