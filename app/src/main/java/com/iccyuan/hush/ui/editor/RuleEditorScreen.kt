@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.iccyuan.hush.ui.editor
import com.iccyuan.hush.data.AppInfo
import com.iccyuan.hush.data.InstalledApps
import com.iccyuan.hush.data.NotificationLogRepository
import com.iccyuan.hush.data.model.NotificationLog
import com.iccyuan.hush.data.model.Rule
import com.iccyuan.hush.engine.Decision
import com.iccyuan.hush.engine.RuleEngine
import com.iccyuan.hush.service.DanmakuController

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
import com.iccyuan.hush.R
import com.iccyuan.hush.data.model.Action
import com.iccyuan.hush.data.model.Condition
import com.iccyuan.hush.data.model.LogicMode
import com.iccyuan.hush.data.model.Trigger
import com.iccyuan.hush.engine.SideEffect
import com.iccyuan.hush.ui.Localize
import com.iccyuan.hush.ui.common.LabeledTextField
import com.iccyuan.hush.ui.components.GlassScaffold
import com.iccyuan.hush.ui.components.HairlineDivider
import com.iccyuan.hush.ui.components.IOSFilledButton
import com.iccyuan.hush.ui.components.IOSRow
import com.iccyuan.hush.ui.components.IOSSegmented
import com.iccyuan.hush.ui.components.IOSSwitch
import com.iccyuan.hush.ui.components.IOSTintedButton
import com.iccyuan.hush.ui.components.InsetGroupedSection
import com.iccyuan.hush.ui.theme.IOSColors

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

            // 条件：按类型自动分组——同类「或」（如多个时间段满足任一），异类「与」。
            InsetGroupedSection(
                header = stringResource(R.string.section_conditions),
                footer = if (rule.conditions.size >= 2) stringResource(R.string.cond_logic_hint) else null,
            ) {
                val groups = rule.conditions.groupBy { it::class }.values.toList()
                groups.forEachIndexed { gi, group ->
                    if (gi > 0) ConditionJoinLabel(stringResource(R.string.cond_join_and))
                    group.forEachIndexed { ci, condition ->
                        if (ci > 0) ConditionJoinLabel(stringResource(R.string.cond_join_or))
                        val (ic, col) = ComponentVisuals.of(condition)
                        IOSRow(
                            title = Localize.summary(condition),
                            icon = ic,
                            iconColor = col,
                            onClick = { editingCondition = condition },
                        )
                    }
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
                // 弹幕依赖「屏蔽」动作，紧随动作列表展示；仅当已添加「屏蔽」动作时出现。
                if (rule.actions.any { it is Action.DiscardAction }) {
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
                    if (rule.showDanmaku && !DanmakuController.canShow(ctx)) {
                        HairlineDivider(startInset = 16.dp)
                        IOSRow(
                            title = stringResource(R.string.grant_overlay),
                            icon = Icons.Filled.OpenInNew,
                            iconColor = IOSColors.Orange,
                            onClick = {
                                ctx.startActivity(DanmakuController.overlaySettingsIntent(ctx))
                            },
                        )
                    }
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
                Column(Modifier.padding(12.dp)) {
                    LabeledTextField(stringResource(R.string.notes), rule.notes, singleLine = false) {
                        vm.setNotes(it)
                    }
                }
            }

            // 实时预览匹配该规则的近期通知。
            PreviewSection(rule)

            // 交互式测试器：输入一条样例通知，查看该规则的处理结果。
            SimulatorSection(rule)

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
    // Webhook 内容较多，改用全屏二级界面；其余动作仍用对话框。
    val webhook = editingAction as? Action.WebhookAction
    if (webhook != null) {
        WebhookEditorScreen(webhook, onActionSave, onActionDelete, onActionDismiss)
    } else {
        ActionEditorDialog(editingAction, onActionSave, onActionDelete, onActionDismiss)
    }

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

/** 条件分组之间的连接词标签：「且」（不同类型间）或「或」（同类型间）。 */
@Composable
private fun ConditionJoinLabel(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 匹配当前（未保存）规则的应用 + 触发器的近期已记录通知。 */
@Composable
private fun PreviewSection(rule: Rule) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val engine = remember { RuleEngine() }
    val logs by androidx.compose.runtime.produceState(
        initialValue = emptyList<NotificationLog>(),
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            NotificationLogRepository.get(context).recent(200)
        }
    }
    // 既没有触发器也没有应用过滤的规则会匹配*所有内容*，因此预览它
    // 只会把整个日志全部倒出来——并非有意义的预览。将这种情况视为无约束。
    val unconstrained = rule.matchesEverything && rule.appPackages.isEmpty()
    // 先算出全部去重后的命中，footer 计数用它的真实总数；列表再截断展示，
    // 否则计数会被 take(15) 一并截断，导致「近期 N 条命中」永远 ≤ 15 而失真。
    val allMatches = remember(rule, logs, unconstrained) {
        if (unconstrained) emptyList()
        else logs
            // 跳过空白通知（既无标题也无正文）——它们会渲染为空行。
            .filter { it.title.isNotBlank() || it.text.isNotBlank() }
            .filter { engine.previewMatches(rule, it.packageName, it.title, it.text) }
            .distinctBy { it.packageName + "|" + it.title + "|" + it.text }
    }
    val matches = allMatches.take(15)
    InsetGroupedSection(
        header = stringResource(R.string.preview_title),
        footer = if (allMatches.isNotEmpty())
            stringResource(R.string.preview_match_count, allMatches.size)
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

/** 交互式测试器：输入一条样例通知，调用引擎模拟并展示命中/改写/副作用结果。 */
@Composable
private fun SimulatorSection(rule: Rule) {
    val engine = remember { RuleEngine() }
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    val pkg = rule.appPackages.firstOrNull() ?: "com.example.app"
    val appName = pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    val decision = remember(rule, title, text) {
        if (title.isBlank() && text.isBlank()) null
        else engine.simulate(rule, pkg, appName, title, text)
    }
    InsetGroupedSection(
        header = stringResource(R.string.sim_title),
        footer = stringResource(R.string.sim_hint),
    ) {
        Column(Modifier.padding(12.dp)) {
            LabeledTextField(stringResource(R.string.sim_sample_title), title) { title = it }
            Spacer(Modifier.height(6.dp))
            LabeledTextField(stringResource(R.string.sim_sample_text), text) { text = it }
        }
        if (decision != null) {
            HairlineDivider(startInset = 16.dp)
            SimResult(decision)
        }
    }
}

@Composable
private fun SimResult(decision: Decision) {
    Column(Modifier.padding(16.dp)) {
        Text(
            stringResource(if (decision.matched) R.string.sim_match else R.string.sim_no_match),
            style = MaterialTheme.typography.bodyLarge,
            color = if (decision.matched) IOSColors.Green else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!decision.matched) return@Column
        val outcome = stringResource(
            when {
                decision.discard -> R.string.outcome_discarded
                decision.needsRepost -> R.string.outcome_modified
                decision.dismiss -> R.string.outcome_dismissed
                decision.snoozeMinutes != null -> R.string.outcome_snoozed
                else -> R.string.outcome_matched
            }
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.sim_outcome, outcome),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        decision.fieldEdits.forEach { (field, value) ->
            Text(
                stringResource(R.string.sim_field, Localize.field(field), value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val effects = decision.sideEffects.mapNotNull { effectName(it) }
        if (effects.isNotEmpty()) {
            Text(
                stringResource(R.string.sim_effects, effects.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun effectName(effect: SideEffect): String? = when (effect) {
    is SideEffect.ReadAloud -> stringResource(R.string.cat_act_readaloud)
    is SideEffect.Toast -> stringResource(R.string.cat_act_toast)
    is SideEffect.Notify -> stringResource(R.string.cat_act_notify)
    is SideEffect.WakeScreen -> stringResource(R.string.cat_act_wake)
    is SideEffect.AutoReply -> stringResource(R.string.cat_act_autoreply)
    is SideEffect.Webhook -> stringResource(R.string.cat_act_webhook)
    is SideEffect.MuteApp -> stringResource(R.string.cat_act_mute)
    is SideEffect.Digest -> stringResource(R.string.cat_act_digest)
    is SideEffect.Danmaku -> stringResource(R.string.danmaku_switch)
}

/** 已选应用以图标 + 名称标签的形式展示；点击任意位置都会打开选择器。 */
@Composable
private fun SelectedAppsChips(packages: List<String>, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val infos by androidx.compose.runtime.produceState(
        initialValue = emptyList<AppInfo>(), packages,
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            InstalledApps.infoFor(context, packages)
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
private fun AppChip(app: AppInfo) {
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
