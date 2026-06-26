@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.buzzkill.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
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
    // Which field the inline wheel is editing (0 = start, 1 = end, null = collapsed).
    var editing by remember { mutableStateOf<Int?>(null) }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeChip(
                label = stringResource(R.string.start_time),
                minuteOfDay = c.startMinute,
                active = editing == 0,
                modifier = Modifier.weight(1f),
            ) { editing = if (editing == 0) null else 0 }
            TimeChip(
                label = stringResource(R.string.end_time),
                minuteOfDay = c.endMinute,
                active = editing == 1,
                modifier = Modifier.weight(1f),
            ) { editing = if (editing == 1) null else 1 }
        }
        // Inline wheel — expands within this same dialog (no second popup).
        androidx.compose.animation.AnimatedVisibility(visible = editing != null) {
            val minutes = if (editing == 1) c.endMinute else c.startMinute
            androidx.compose.runtime.key(editing) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    TimeWheel(
                        hour = minutes / 60,
                        minute = minutes % 60,
                        onChange = { h, m ->
                            val v = h * 60 + m
                            if (editing == 1) onChange(c.copy(endMinute = v))
                            else onChange(c.copy(startMinute = v))
                        },
                    )
                }
            }
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

/** A tappable HH:MM chip; tapping toggles the inline wheel for this field. */
@Composable
private fun TimeChip(
    label: String,
    minuteOfDay: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        val border by androidx.compose.animation.animateColorAsState(
            if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            label = "timeChipBorder",
        )
        val bg = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent
        Box(
            Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(bg)
                .border(1.dp, border, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
