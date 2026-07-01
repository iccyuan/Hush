package com.iccyuan.hush.ui.editor
import com.iccyuan.hush.engine.TextMatcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
