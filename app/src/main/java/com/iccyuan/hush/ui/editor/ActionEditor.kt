package com.iccyuan.hush.ui.editor
import com.iccyuan.hush.engine.TextMatcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
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
import com.iccyuan.hush.ui.components.DialogActions
import com.iccyuan.hush.ui.components.GlassDialog
import com.iccyuan.hush.data.model.Action
import com.iccyuan.hush.data.model.HttpHeader
import com.iccyuan.hush.data.model.HttpMethod
import com.iccyuan.hush.data.model.Importance
import com.iccyuan.hush.data.model.NotificationField
import com.iccyuan.hush.data.model.VibrationPreset
import com.iccyuan.hush.ui.Localize
import com.iccyuan.hush.ui.common.EnumDropdown
import com.iccyuan.hush.ui.common.IntField
import com.iccyuan.hush.ui.common.LabeledTextField
import com.iccyuan.hush.ui.common.SwitchRow
import com.iccyuan.hush.ui.theme.IOSColors

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
            val patternError = if (a.isRegex) {
                TextMatcher.regexError(a.pattern)
                    ?.let { stringResource(R.string.err_invalid_regex, it) }
            } else null
            LabeledTextField(stringResource(R.string.find), a.pattern, error = patternError) {
                onChange(a.copy(pattern = it))
            }
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
        is Action.NotifyAction -> Column {
            LabeledTextField(stringResource(R.string.notify_template), a.template, singleLine = false) {
                onChange(a.copy(template = it))
            }
            TemplateHint()
        }
        is Action.SetVariableAction -> Column {
            val nameError = if (a.name.isBlank() || a.name.any { it.isWhitespace() })
                stringResource(R.string.err_invalid_var_name) else null
            LabeledTextField(stringResource(R.string.variable_name), a.name, error = nameError) {
                onChange(a.copy(name = it))
            }
            Spacer(Modifier.height(6.dp))
            LabeledTextField(stringResource(R.string.value_template), a.valueTemplate) {
                onChange(a.copy(valueTemplate = it))
            }
            TemplateHint()
        }
        is Action.RunTaskerAction -> LabeledTextField(stringResource(R.string.tasker_task), a.taskName) {
            onChange(a.copy(taskName = it))
        }
        is Action.WebhookAction -> WebhookFields(a) { onChange(it) }
        is Action.MuteAppAction -> Text(stringResource(R.string.mute_explain))
        is Action.DigestAction -> Column {
            IntField(stringResource(R.string.digest_window_minutes), a.windowMinutes) {
                onChange(a.copy(windowMinutes = it))
            }
            Spacer(Modifier.height(6.dp))
            LabeledTextField(stringResource(R.string.template), a.template) {
                onChange(a.copy(template = it))
            }
            TemplateHint()
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

/** 类 Postman 的 HTTP 请求编辑：URL + 方法(GET/POST) + 自定义请求头 + 请求体(仅 POST)。 */
@Composable
private fun WebhookFields(a: Action.WebhookAction, onChange: (Action.WebhookAction) -> Unit) {
    Column {
        val urlError = if (a.url.isNotBlank() && !android.util.Patterns.WEB_URL.matcher(a.url).matches())
            stringResource(R.string.err_invalid_url) else null
        LabeledTextField(stringResource(R.string.url), a.url, error = urlError) {
            onChange(a.copy(url = it))
        }
        Spacer(Modifier.height(6.dp))
        // 仅支持 GET / POST。
        EnumDropdown(
            label = stringResource(R.string.method),
            options = listOf(HttpMethod.GET, HttpMethod.POST),
            selected = if (a.method == HttpMethod.POST) HttpMethod.POST else HttpMethod.GET,
            optionLabel = { it.name },
            onSelected = { onChange(a.copy(method = it)) },
        )

        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.http_headers),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        a.headers.forEachIndexed { i, h ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LabeledTextField(
                    stringResource(R.string.header_name), h.name, modifier = Modifier.weight(1f),
                ) { v -> onChange(a.copy(headers = a.headers.mapAt(i) { it.copy(name = v) })) }
                LabeledTextField(
                    stringResource(R.string.header_value), h.value, modifier = Modifier.weight(1.3f),
                ) { v -> onChange(a.copy(headers = a.headers.mapAt(i) { it.copy(value = v) })) }
                IconButton(onClick = { onChange(a.copy(headers = a.headers.filterIndexed { j, _ -> j != i })) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.delete))
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onChange(a.copy(headers = a.headers + HttpHeader())) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = IOSColors.Blue)
            Text(
                stringResource(R.string.add_header),
                style = MaterialTheme.typography.bodyMedium,
                color = IOSColors.Blue,
            )
        }

        // 请求体仅 POST 携带（GET 不发；要传值请用 URL 查询参数）。
        if (a.method == HttpMethod.POST) {
            Spacer(Modifier.height(6.dp))
            LabeledTextField(stringResource(R.string.body_template), a.bodyTemplate, singleLine = false) {
                onChange(a.copy(bodyTemplate = it))
            }
        } else {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.webhook_get_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TemplateHint()
    }
}

/** 返回替换了第 [index] 项的新列表（其余不变）。 */
private fun <T> List<T>.mapAt(index: Int, transform: (T) -> T): List<T> =
    mapIndexed { i, item -> if (i == index) transform(item) else item }

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
        is Action.NotifyAction -> R.string.cat_act_notify
        is Action.SetVariableAction -> R.string.cat_act_setvar
        is Action.RunTaskerAction -> R.string.cat_act_tasker
        is Action.WebhookAction -> R.string.cat_act_webhook
        is Action.MuteAppAction -> R.string.cat_act_mute
        is Action.DigestAction -> R.string.cat_act_digest
    }
)
