@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.buzzkill.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buzzkill.R
import com.buzzkill.data.model.Condition
import com.buzzkill.data.model.DayType
import com.buzzkill.ui.Localize
import com.buzzkill.ui.common.IntField
import com.buzzkill.ui.common.SwitchRow
import com.buzzkill.ui.components.DialogActions
import com.buzzkill.ui.components.GlassDialog
import com.buzzkill.ui.components.TimeWheel

@Composable
fun ConditionEditorDialog(
    existing: Condition?,
    onSave: (Condition) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    if (existing == null) return
    var draft by remember(existing.id) { mutableStateOf(existing) }

    GlassDialog(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.edit_condition),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Column {
            when (val c = draft) {
                is Condition.TimeCondition -> TimeConditionFields(c) { draft = it }
                is Condition.HolidayCondition -> HolidayConditionFields(c) { draft = it }
                is Condition.ChargingCondition -> SwitchRow(
                    stringResource(R.string.must_be_charging), c.mustBeCharging
                ) { draft = c.copy(mustBeCharging = it) }
                is Condition.ScreenCondition -> SwitchRow(
                    stringResource(R.string.screen_must_on), c.mustBeOn
                ) { draft = c.copy(mustBeOn = it) }
                is Condition.BatteryLevelCondition -> {
                    IntField(stringResource(R.string.percent), c.percent) { draft = c.copy(percent = it) }
                    SwitchRow(stringResource(R.string.when_below), c.whenBelow) {
                        draft = c.copy(whenBelow = it)
                    }
                }
                is Condition.CooldownCondition ->
                    IntField(stringResource(R.string.seconds), c.seconds) { draft = c.copy(seconds = it) }
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
private fun TimeConditionFields(c: Condition.TimeCondition, onChange: (Condition.TimeCondition) -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeField(
                label = stringResource(R.string.start_time),
                minuteOfDay = c.startMinute,
                modifier = Modifier.weight(1f),
            ) { onChange(c.copy(startMinute = it)) }
            TimeField(
                label = stringResource(R.string.end_time),
                minuteOfDay = c.endMinute,
                modifier = Modifier.weight(1f),
            ) { onChange(c.copy(endMinute = it)) }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.cond_days), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        val abbr = stringArrayResource(R.array.weekday_abbr)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (iso in 1..7) {
                FilterChip(
                    selected = c.days.contains(iso),
                    onClick = {
                        val days = c.days.toMutableSet()
                        if (!days.add(iso)) days.remove(iso)
                        onChange(c.copy(days = days))
                    },
                    label = { Text(abbr[iso - 1]) },
                )
            }
        }
    }
}

@Composable
private fun HolidayConditionFields(
    c: Condition.HolidayCondition,
    onChange: (Condition.HolidayCondition) -> Unit,
) {
    Column {
        Text(stringResource(R.string.section_day_types), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayType.entries.forEach { type ->
                FilterChip(
                    selected = c.dayTypes.contains(type),
                    onClick = {
                        val set = c.dayTypes.toMutableSet()
                        if (!set.add(type)) set.remove(type)
                        onChange(c.copy(dayTypes = set))
                    },
                    label = { Text(Localize.dayType(type)) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.holiday_coverage),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A clickable HH:MM field backed by an iOS-style wheel time picker. */
@Composable
private fun TimeField(
    label: String,
    minuteOfDay: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text("%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60))
        }
    }
    if (showPicker) {
        var hour by remember { mutableStateOf(minuteOfDay / 60) }
        var minute by remember { mutableStateOf(minuteOfDay % 60) }
        GlassDialog(onDismiss = { showPicker = false }) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            TimeWheel(hour = hour, minute = minute, onChange = { h, m -> hour = h; minute = m })
            DialogActions(
                confirmText = stringResource(R.string.done),
                onConfirm = {
                    onChange(hour * 60 + minute)
                    showPicker = false
                },
                secondaryText = stringResource(R.string.cancel),
                onSecondary = { showPicker = false },
            )
        }
    }
}
