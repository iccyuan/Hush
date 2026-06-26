@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.buzzkill.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buzzkill.R
import com.buzzkill.data.model.Action
import com.buzzkill.data.model.Condition
import com.buzzkill.data.model.LogicMode
import com.buzzkill.data.model.Trigger
import com.buzzkill.ui.Localize
import com.buzzkill.ui.common.LabeledTextField
import com.buzzkill.ui.components.GlassScaffold
import com.buzzkill.ui.components.HairlineDivider
import com.buzzkill.ui.components.IOSFilledButton
import com.buzzkill.ui.components.IOSRow
import com.buzzkill.ui.components.IOSSegmented
import com.buzzkill.ui.components.IOSSwitch
import com.buzzkill.ui.components.IOSTintedButton
import com.buzzkill.ui.components.InsetGroupedSection
import com.buzzkill.ui.theme.IOSColors

private enum class AddKind { TRIGGER, CONDITION, ACTION }

@Composable
fun RuleEditorScreen(
    ruleId: Long,
    onDone: () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    vm: RuleEditorViewModel = viewModel(),
) {
    LaunchedEffect(ruleId) { vm.load(ruleId) }
    val rule by vm.rule.collectAsStateWithLifecycle()

    var editingTrigger by remember { mutableStateOf<Trigger?>(null) }
    var editingCondition by remember { mutableStateOf<Condition?>(null) }
    var editingAction by remember { mutableStateOf<Action?>(null) }
    var addKind by remember { mutableStateOf<AddKind?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_rule_title)) },
            text = { Text(stringResource(R.string.delete_rule_msg)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; vm.delete(onDone) }) {
                    Text(stringResource(R.string.delete), color = IOSColors.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    GlassScaffold(
        title = stringResource(if (ruleId == 0L) R.string.new_rule else R.string.edit_rule),
        // 作为标签页时（存在 bottomBar）不显示返回箭头；作为推入页面时则显示。
        onBack = if (bottomBar != null) null else onDone,
        bottomBar = bottomBar,
        actions = {
            TextButton(onClick = { vm.save(onDone) }) { Text(stringResource(R.string.save)) }
        },
        overlay = {
            EditorOverlays(
                editingTrigger = editingTrigger,
                onTriggerSave = { vm.updateTrigger(it); editingTrigger = null },
                onTriggerDelete = { editingTrigger?.let { vm.removeTrigger(it.id) }; editingTrigger = null },
                onTriggerDismiss = { editingTrigger = null },
                editingCondition = editingCondition,
                onConditionSave = { vm.updateCondition(it); editingCondition = null },
                onConditionDelete = { editingCondition?.let { vm.removeCondition(it.id) }; editingCondition = null },
                onConditionDismiss = { editingCondition = null },
                editingAction = editingAction,
                onActionSave = { vm.updateAction(it); editingAction = null },
                onActionDelete = { editingAction?.let { vm.removeAction(it.id) }; editingAction = null },
                onActionDismiss = { editingAction = null },
                addKind = addKind,
                onAddTrigger = { vm.addTrigger(it); addKind = null; editingTrigger = it },
                onAddCondition = { vm.addCondition(it); addKind = null; editingCondition = it },
                onAddAction = { vm.addAction(it); addKind = null; editingAction = it },
                onAddDismiss = { addKind = null },
                showAppPicker = showAppPicker,
                appPackages = rule.appPackages,
                onAppsConfirm = { vm.setApps(it); showAppPicker = false },
                onAppsDismiss = { showAppPicker = false },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // 名称
            InsetGroupedSection {
                Column(Modifier.padding(12.dp)) {
                    LabeledTextField(stringResource(R.string.rule_name), rule.name) { vm.setName(it) }
                }
            }

            // 应用
            InsetGroupedSection(header = stringResource(R.string.section_apps)) {
                if (rule.appPackages.isEmpty()) {
                    IOSRow(
                        title = stringResource(R.string.applies_all_apps),
                        icon = Icons.Filled.Apps,
                        iconColor = IOSColors.Blue,
                        onClick = { showAppPicker = true },
                    )
                } else {
                    SelectedAppsChips(
                        packages = rule.appPackages,
                        onClick = { showAppPicker = true },
                    )
                }
            }

            // 触发器
            InsetGroupedSection(
                header = stringResource(R.string.section_triggers),
                footer = if (rule.triggers.isEmpty()) stringResource(R.string.no_triggers_hint) else null,
            ) {
                Column(Modifier.padding(12.dp)) {
                    IOSSegmented(
                        options = LogicMode.entries,
                        selected = rule.triggerLogic,
                        label = { stringResource(Localize.logicRes(it)) },
                        onSelect = { vm.setTriggerLogic(it) },
                    )
                }
                rule.triggers.forEach { trigger ->
                    HairlineDivider(startInset = 16.dp)
                    val (ic, col) = ComponentVisuals.of(trigger)
                    IOSRow(title = Localize.summary(trigger), icon = ic, iconColor = col, onClick = { editingTrigger = trigger })
                }
                HairlineDivider(startInset = 16.dp)
                AddRow(stringResource(R.string.add_trigger), Icons.Filled.FilterAlt, IOSColors.Blue) {
                    addKind = AddKind.TRIGGER
                }
            }

            // 条件
            InsetGroupedSection(header = stringResource(R.string.section_conditions)) {
                rule.conditions.forEachIndexed { i, condition ->
                    if (i > 0) HairlineDivider(startInset = 16.dp)
                    val (ic, col) = ComponentVisuals.of(condition)
                    IOSRow(title = Localize.summary(condition), icon = ic, iconColor = col, onClick = { editingCondition = condition })
                }
                if (rule.conditions.isNotEmpty()) HairlineDivider(startInset = 16.dp)
                AddRow(stringResource(R.string.add_condition), Icons.Filled.Tune, IOSColors.Orange) {
                    addKind = AddKind.CONDITION
                }
            }

            // 动作
            InsetGroupedSection(
                header = stringResource(R.string.section_actions),
                footer = if (rule.actions.isEmpty()) stringResource(R.string.no_actions_hint) else null,
            ) {
                rule.actions.forEachIndexed { i, action ->
                    if (i > 0) HairlineDivider(startInset = 16.dp)
                    val (ic, col) = ComponentVisuals.of(action)
                    IOSRow(title = Localize.summary(action), icon = ic, iconColor = col, onClick = { editingAction = action })
                }
                if (rule.actions.isNotEmpty()) HairlineDivider(startInset = 16.dp)
                AddRow(stringResource(R.string.add_action), Icons.Filled.Bolt, IOSColors.Green) {
                    addKind = AddKind.ACTION
                }
            }

            // 选项
            InsetGroupedSection(header = stringResource(R.string.section_options)) {
                IOSRow(
                    title = stringResource(R.string.enabled),
                    trailing = { IOSSwitch(rule.enabled) { vm.setEnabled(it) } },
                )
                HairlineDivider(startInset = 16.dp)
                IOSRow(
                    title = stringResource(R.string.stop_processing),
                    trailing = { IOSSwitch(rule.stopProcessing) { vm.setStopProcessing(it) } },
                )
                HairlineDivider(startInset = 16.dp)
                IOSRow(
                    title = stringResource(R.string.danmaku_switch),
                    subtitle = stringResource(R.string.danmaku_switch_hint),
                    icon = Icons.Filled.Subtitles,
                    iconColor = IOSColors.Purple,
                    trailing = { IOSSwitch(rule.showDanmaku) { vm.setShowDanmaku(it) } },
                )
                // 当弹幕已开启但缺少悬浮窗权限时，提供授予权限的入口。
                val ctx = androidx.compose.ui.platform.LocalContext.current
                if (rule.showDanmaku && !com.buzzkill.service.DanmakuController.canShow(ctx)) {
                    HairlineDivider(startInset = 16.dp)
                    IOSRow(
                        title = stringResource(R.string.grant_overlay),
                        icon = Icons.Filled.OpenInNew,
                        iconColor = IOSColors.Orange,
                        onClick = {
                            ctx.startActivity(com.buzzkill.service.DanmakuController.overlaySettingsIntent(ctx))
                        },
                    )
                }
                HairlineDivider(startInset = 16.dp)
                Column(Modifier.padding(12.dp)) {
                    LabeledTextField(stringResource(R.string.notes), rule.notes, singleLine = false) {
                        vm.setNotes(it)
                    }
                }
            }

            // 实时预览匹配该规则的近期通知。
            PreviewSection(rule)

            if (ruleId != 0L) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    IOSFilledButton(
                        text = stringResource(R.string.delete),
                        destructive = true,
                        onClick = { showDeleteConfirm = true },
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

}

/** 所有编辑器对话框/弹层，渲染在 GlassScaffold 的 overlay 插槽内，从而获得磨砂效果。 */
@Composable
private fun EditorOverlays(
    editingTrigger: Trigger?,
    onTriggerSave: (Trigger) -> Unit,
    onTriggerDelete: () -> Unit,
    onTriggerDismiss: () -> Unit,
    editingCondition: Condition?,
    onConditionSave: (Condition) -> Unit,
    onConditionDelete: () -> Unit,
    onConditionDismiss: () -> Unit,
    editingAction: Action?,
    onActionSave: (Action) -> Unit,
    onActionDelete: () -> Unit,
    onActionDismiss: () -> Unit,
    addKind: AddKind?,
    onAddTrigger: (Trigger) -> Unit,
    onAddCondition: (Condition) -> Unit,
    onAddAction: (Action) -> Unit,
    onAddDismiss: () -> Unit,
    showAppPicker: Boolean,
    appPackages: List<String>,
    onAppsConfirm: (List<String>) -> Unit,
    onAppsDismiss: () -> Unit,
) {
    TriggerEditorDialog(editingTrigger, onTriggerSave, onTriggerDelete, onTriggerDismiss)
    ConditionEditorDialog(editingCondition, onConditionSave, onConditionDelete, onConditionDismiss)
    ActionEditorDialog(editingAction, onActionSave, onActionDelete, onActionDismiss)

    when (addKind) {
        AddKind.TRIGGER -> AddComponentSheet(
            stringResource(R.string.add_trigger), ComponentCatalog.triggers, onAddTrigger, onAddDismiss
        )
        AddKind.CONDITION -> AddComponentSheet(
            stringResource(R.string.add_condition), ComponentCatalog.conditions, onAddCondition, onAddDismiss
        )
        AddKind.ACTION -> AddComponentSheet(
            stringResource(R.string.add_action), ComponentCatalog.actions, onAddAction, onAddDismiss
        )
        null -> Unit
    }

    if (showAppPicker) {
        AppPickerScreen(appPackages, onAppsConfirm, onAppsDismiss)
    }
}

@Composable
private fun AddRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    IOSRow(title = label, icon = icon, iconColor = color, onClick = onClick)
}

/** 匹配当前（未保存）规则的应用 + 触发器的近期已记录通知。 */
@Composable
private fun PreviewSection(rule: com.buzzkill.data.model.Rule) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val engine = remember { com.buzzkill.engine.RuleEngine() }
    val logs by androidx.compose.runtime.produceState(
        initialValue = emptyList<com.buzzkill.data.model.NotificationLog>(),
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.buzzkill.data.NotificationLogRepository.get(context).recent(200)
        }
    }
    // 既没有触发器也没有应用过滤的规则会匹配*所有内容*，因此预览它
    // 只会把整个日志全部倒出来——并非有意义的预览。将这种情况视为无约束。
    val unconstrained = rule.matchesEverything && rule.appPackages.isEmpty()
    val matches = remember(rule, logs, unconstrained) {
        if (unconstrained) emptyList()
        else logs
            // 跳过空白通知（既无标题也无正文）——它们会渲染为空行。
            .filter { it.title.isNotBlank() || it.text.isNotBlank() }
            .filter { engine.previewMatches(rule, it.packageName, it.title, it.text) }
            .distinctBy { it.packageName + "|" + it.title + "|" + it.text }
            .take(15)
    }
    InsetGroupedSection(
        header = stringResource(R.string.preview_title),
        footer = if (matches.isNotEmpty())
            stringResource(R.string.preview_match_count, matches.size)
        else stringResource(R.string.preview_hint),
    ) {
        when {
            unconstrained -> IOSRow(title = stringResource(R.string.preview_unconstrained))
            matches.isEmpty() -> IOSRow(title = stringResource(R.string.preview_none))
            else -> matches.forEachIndexed { i, log ->
                if (i > 0) HairlineDivider(startInset = 16.dp)
                val sub = listOf(log.title, log.text).filter { it.isNotBlank() }.joinToString(" · ")
                IOSRow(title = log.appName.ifBlank { log.packageName }, subtitle = sub.ifBlank { null })
            }
        }
    }
}

/** 已选应用以图标 + 名称标签的形式展示；点击任意位置都会打开选择器。 */
@Composable
private fun SelectedAppsChips(packages: List<String>, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val infos by androidx.compose.runtime.produceState(
        initialValue = emptyList<com.buzzkill.data.AppInfo>(), packages,
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.buzzkill.data.InstalledApps.infoFor(context, packages)
        }
    }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        infos.forEach { AppChip(it) }
    }
}

@Composable
private fun AppChip(app: com.buzzkill.data.AppInfo) {
    val bmp = remember(app.packageName) {
        runCatching { app.icon?.toBitmap(48, 48)?.asImageBitmap() }.getOrNull()
    }
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (bmp != null) {
            androidx.compose.foundation.Image(bmp, null, Modifier.size(20.dp))
        } else {
            androidx.compose.material3.Icon(Icons.Filled.Apps, null, Modifier.size(20.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(app.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}
