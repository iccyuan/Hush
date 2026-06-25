package com.buzzkill.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buzzkill.R
import com.buzzkill.ui.components.DialogActions
import com.buzzkill.ui.components.GlassDialog
import com.buzzkill.data.model.Action
import com.buzzkill.data.model.HttpMethod
import com.buzzkill.data.model.Importance
import com.buzzkill.data.model.NotificationField
import com.buzzkill.data.model.VibrationPreset
import com.buzzkill.ui.Localize
import com.buzzkill.ui.common.EnumDropdown
import com.buzzkill.ui.common.IntField
import com.buzzkill.ui.common.LabeledTextField
import com.buzzkill.ui.common.SwitchRow

@Composable
fun ActionEditorDialog(
    existing: Action?,
    onSave: (Action) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    if (existing == null) return
    var draft by remember(existing.id) { mutableStateOf(existing) }

    GlassDialog(onDismiss = onDismiss) {
        Text(
            actionTitle(draft),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            ActionFields(draft) { draft = it }
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
private fun fieldDropdown(selected: NotificationField, onSelect: (NotificationField) -> Unit) {
    val labels = NotificationField.entries.associateWith { stringResource(Localize.fieldRes(it)) }
    EnumDropdown(
        label = stringResource(R.string.field),
        options = NotificationField.entries,
        selected = selected,
        optionLabel = { labels.getValue(it) },
        onSelected = onSelect,
    )
}

@Composable
private fun ActionFields(action: Action, onChange: (Action) -> Unit) {
    when (val a = action) {
        is Action.ReplaceTextAction -> Column {
            fieldDropdown(a.field) { onChange(a.copy(field = it)) }
            Spacer(Modifier.height(6.dp))
            LabeledTextField(stringResource(R.string.find), a.pattern) { onChange(a.copy(pattern = it)) }
            Spacer(Modifier.height(6.dp))
            LabeledTextField(stringResource(R.string.replace_with), a.replacement) {
                onChange(a.copy(replacement = it))
            }
            SwitchRow(stringResource(R.string.regex), a.isRegex) { onChange(a.copy(isRegex = it)) }
            SwitchRow(stringResource(R.string.case_sensitive), a.caseSensitive) {
                onChange(a.copy(caseSensitive = it))
            }
        }
        is Action.SetFieldAction -> Column {
            fieldDropdown(a.field) { onChange(a.copy(field = it)) }
            Spacer(Modifier.height(6.dp))
            LabeledTextField(stringResource(R.string.template), a.template, singleLine = false) {
                onChange(a.copy(template = it))
            }
            TemplateHint()
        }
        is Action.DiscardAction -> Text(stringResource(R.string.discard_explain))
        is Action.DismissAction -> IntField(stringResource(R.string.delay_ms), a.delayMs.toInt()) {
            onChange(a.copy(delayMs = it.toLong()))
        }
        is Action.SnoozeAction -> IntField(stringResource(R.string.minutes), a.minutes) {
            onChange(a.copy(minutes = it))
        }
        is Action.MarkImportantAction -> Column {
            val labels = Importance.entries.associateWith { stringResource(Localize.importanceRes(it)) }
            EnumDropdown(
                label = stringResource(R.string.importance),
                options = Importance.entries,
                selected = a.importance,
                optionLabel = { labels.getValue(it) },
                onSelected = { onChange(a.copy(importance = it)) },
            )
            SwitchRow(stringResource(R.string.bypass_dnd), a.bypassDnd) { onChange(a.copy(bypassDnd = it)) }
        }
        is Action.SoundVibrationAction -> Column {
            SwitchRow(stringResource(R.string.silence), a.silent) { onChange(a.copy(silent = it)) }
            if (!a.silent) {
                Spacer(Modifier.height(6.dp))
                val labels = VibrationPreset.entries.associateWith { stringResource(Localize.vibrationRes(it)) }
                EnumDropdown(
                    label = stringResource(R.string.vibration),
                    options = VibrationPreset.entries,
                    selected = a.vibration,
                    optionLabel = { labels.getValue(it) },
                    onSelected = { onChange(a.copy(vibration = it)) },
                )
                Spacer(Modifier.height(6.dp))
                LabeledTextField(stringResource(R.string.sound_uri), a.soundUri.orEmpty()) {
                    onChange(a.copy(soundUri = it.ifBlank { null }))
                }
            }
        }
        is Action.AutoReplyAction -> Column {
            LabeledTextField(stringResource(R.string.reply_message), a.message, singleLine = false) {
                onChange(a.copy(message = it))
            }
            TemplateHint()
        }
        is Action.ReadAloudAction -> Column {
            LabeledTextField(stringResource(R.string.spoken_template), a.template, singleLine = false) {
                onChange(a.copy(template = it))
            }
            TemplateHint()
        }
        is Action.WakeScreenAction -> IntField(stringResource(R.string.duration_ms), a.durationMs.toInt()) {
            onChange(a.copy(durationMs = it.toLong()))
        }
        is Action.ToastAction -> Column {
            LabeledTextField(stringResource(R.string.toast_template), a.template) {
                onChange(a.copy(template = it))
            }
            TemplateHint()
        }
        is Action.SetVariableAction -> Column {
            LabeledTextField(stringResource(R.string.variable_name), a.name) { onChange(a.copy(name = it)) }
            Spacer(Modifier.height(6.dp))
            LabeledTextField(stringResource(R.string.value_template), a.valueTemplate) {
                onChange(a.copy(valueTemplate = it))
            }
            TemplateHint()
        }
        is Action.RunTaskerAction -> LabeledTextField(stringResource(R.string.tasker_task), a.taskName) {
            onChange(a.copy(taskName = it))
        }
        is Action.WebhookAction -> Column {
            LabeledTextField(stringResource(R.string.url), a.url) { onChange(a.copy(url = it)) }
            Spacer(Modifier.height(6.dp))
            EnumDropdown(
                label = stringResource(R.string.method),
                options = HttpMethod.entries,
                selected = a.method,
                optionLabel = { it.name },
                onSelected = { onChange(a.copy(method = it)) },
            )
            Spacer(Modifier.height(6.dp))
            LabeledTextField(stringResource(R.string.body_template), a.bodyTemplate, singleLine = false) {
                onChange(a.copy(bodyTemplate = it))
            }
            TemplateHint()
        }
        is Action.MuteAppAction -> IntField(stringResource(R.string.minutes), a.minutes) {
            onChange(a.copy(minutes = it))
        }
        is Action.DanmakuAction -> Column {
            LabeledTextField(stringResource(R.string.danmaku_template), a.template, singleLine = false) {
                onChange(a.copy(template = it))
            }
            TemplateHint()
            val context = androidx.compose.ui.platform.LocalContext.current
            if (!com.buzzkill.service.DanmakuController.canShow(context)) {
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.TextButton(
                    onClick = {
                        context.startActivity(com.buzzkill.service.DanmakuController.overlaySettingsIntent(context))
                    },
                ) { Text(stringResource(R.string.grant_overlay)) }
            }
        }
    }
}

@Composable
private fun TemplateHint() {
    Spacer(Modifier.height(6.dp))
    Text(
        stringResource(R.string.template_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun actionTitle(action: Action): String = stringResource(
    when (action) {
        is Action.ReplaceTextAction -> R.string.cat_act_replace
        is Action.SetFieldAction -> R.string.cat_act_setfield
        is Action.DiscardAction -> R.string.cat_act_discard
        is Action.DismissAction -> R.string.cat_act_dismiss
        is Action.SnoozeAction -> R.string.cat_act_snooze
        is Action.MarkImportantAction -> R.string.cat_act_importance
        is Action.SoundVibrationAction -> R.string.cat_act_sound
        is Action.AutoReplyAction -> R.string.cat_act_autoreply
        is Action.ReadAloudAction -> R.string.cat_act_readaloud
        is Action.WakeScreenAction -> R.string.cat_act_wake
        is Action.ToastAction -> R.string.cat_act_toast
        is Action.SetVariableAction -> R.string.cat_act_setvar
        is Action.RunTaskerAction -> R.string.cat_act_tasker
        is Action.WebhookAction -> R.string.cat_act_webhook
        is Action.MuteAppAction -> R.string.cat_act_mute
        is Action.DanmakuAction -> R.string.cat_act_danmaku
    }
)
