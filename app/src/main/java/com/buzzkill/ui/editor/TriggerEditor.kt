package com.buzzkill.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.buzzkill.data.model.MatchMode
import com.buzzkill.data.model.NotificationField
import com.buzzkill.data.model.Trigger
import com.buzzkill.ui.Localize
import com.buzzkill.ui.common.EnumDropdown
import com.buzzkill.ui.common.LabeledTextField
import com.buzzkill.ui.common.SwitchRow
import com.buzzkill.ui.components.DialogActions
import com.buzzkill.ui.components.GlassDialog
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
            com.buzzkill.engine.TextMatcher.regexError(t.query)
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
