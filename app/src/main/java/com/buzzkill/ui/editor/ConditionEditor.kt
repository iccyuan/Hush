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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.graphics.Color
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
import com.buzzkill.ui.theme.FontSizes
import com.buzzkill.ui.theme.IOSColors

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
        Spacer(Modifier.height(8.dp))
        // Tap to expand an inline month calendar with holidays marked (one dialog).
        var showCalendar by remember { mutableStateOf(false) }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .clickable { showCalendar = !showCalendar }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.holiday_view_calendar),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.Icon(
                if (showCalendar) androidx.compose.material.icons.Icons.Filled.ExpandLess
                else androidx.compose.material.icons.Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        androidx.compose.animation.AnimatedVisibility(visible = showCalendar) {
            HolidayCalendar()
        }
    }
}

/** An inline month calendar with each day classified/marked via [com.buzzkill.data.HolidayProvider]. */
@Composable
private fun HolidayCalendar() {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Ensure holiday data is loaded (no-op if already loaded).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.buzzkill.data.HolidayProvider.ensureLoaded(context)
        }
    }
    val now = remember { java.util.Calendar.getInstance() }
    var ym by remember { mutableStateOf(now.get(java.util.Calendar.YEAR) to (now.get(java.util.Calendar.MONTH) + 1)) }
    val (year, month) = ym
    val todayKey = remember {
        "%04d-%02d-%02d".format(
            now.get(java.util.Calendar.YEAR),
            now.get(java.util.Calendar.MONTH) + 1,
            now.get(java.util.Calendar.DAY_OF_MONTH),
        )
    }

    Column(Modifier.padding(top = 4.dp)) {
        // Month header with prev/next navigation.
        Row(verticalAlignment = Alignment.CenterVertically) {
            CalNavButton(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowLeft) {
                ym = if (month == 1) (year - 1) to 12 else year to (month - 1)
            }
            Text(
                "%d-%02d".format(year, month),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            CalNavButton(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight) {
                ym = if (month == 12) (year + 1) to 1 else year to (month + 1)
            }
        }
        Spacer(Modifier.height(4.dp))
        // Weekday header (Mon..Sun).
        val abbr = stringArrayResource(R.array.weekday_abbr)
        Row(Modifier.fillMaxWidth()) {
            for (i in 0..6) {
                Text(
                    abbr[i],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Day grid.
        for (week in monthCells(year, month).chunked(7)) {
            Row(Modifier.fillMaxWidth()) {
                for (day in week) {
                    Box(Modifier.weight(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            val iso = isoDayOfWeek(year, month, day)
                            val type = com.buzzkill.data.HolidayProvider.dayType(year, month, day, iso)
                            val key = "%04d-%02d-%02d".format(year, month, day)
                            DayCell(day, type, isToday = key == todayKey)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Legend.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(IOSColors.Red, stringResource(R.string.daytype_legal_holiday))
            LegendDot(IOSColors.Orange, stringResource(R.string.daytype_makeup_workday))
        }
    }
}

@Composable
private fun CalNavButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun DayCell(day: Int, type: com.buzzkill.data.model.DayType, isToday: Boolean) {
    val red = IOSColors.Red
    val orange = IOSColors.Orange
    val (bg, fg, mark) = when (type) {
        com.buzzkill.data.model.DayType.LEGAL_HOLIDAY ->
            Triple(red.copy(alpha = 0.14f), red, stringResource(R.string.holiday_mark_off))
        com.buzzkill.data.model.DayType.MAKEUP_WORKDAY ->
            Triple(orange.copy(alpha = 0.14f), orange, stringResource(R.string.holiday_mark_work))
        com.buzzkill.data.model.DayType.WEEKEND ->
            Triple(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant, null)
        com.buzzkill.data.model.DayType.WORKDAY ->
            Triple(Color.Transparent, MaterialTheme.colorScheme.onSurface, null)
    }
    Box(
        Modifier
            .size(36.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
            .background(bg)
            .then(
                if (isToday) Modifier.border(
                    1.5.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(9.dp)
                ) else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            fontWeight = if (type == com.buzzkill.data.model.DayType.LEGAL_HOLIDAY) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (mark != null) {
            Text(
                mark,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontSize = FontSizes.Tiny,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Cells for a month grid: leading nulls to align the 1st under its weekday (Mon-first), then days. */
private fun monthCells(year: Int, month: Int): List<Int?> {
    val daysInMonth = run {
        val c = java.util.Calendar.getInstance()
        c.clear(); c.set(year, month - 1, 1)
        c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    }
    val leading = isoDayOfWeek(year, month, 1) - 1
    val cells = ArrayList<Int?>(leading + daysInMonth)
    repeat(leading) { cells.add(null) }
    for (d in 1..daysInMonth) cells.add(d)
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

/** ISO day of week (Mon=1 … Sun=7) for a date. */
private fun isoDayOfWeek(year: Int, month: Int, day: Int): Int {
    val c = java.util.Calendar.getInstance()
    c.clear(); c.set(year, month - 1, day)
    return ((c.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7) + 1
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
