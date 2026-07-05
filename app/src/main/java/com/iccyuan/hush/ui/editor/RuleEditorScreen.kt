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
import com.iccyuan.hush.engine.DeviceContext
import com.iccyuan.hush.engine.RuleEngine
import com.iccyuan.hush.service.DanmakuController
import com.iccyuan.hush.service.DeviceState

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Edit
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
import com.iccyuan.hush.data.model.GapOp
import com.iccyuan.hush.data.model.LogicMode
import com.iccyuan.hush.data.model.Trigger
import com.iccyuan.hush.data.model.isEventDriven
import com.iccyuan.hush.ui.common.EnumDropdown
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
import com.iccyuan.hush.ui.components.cardFrost
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

            // 触发器（置顶：它决定规则的性质）
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

            // 应用：仅对「基于通知」的规则有意义；事件驱动规则（Wi-Fi / 位置）不挑应用，隐藏此区。
            if (!rule.isEventDriven) {
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
            }

            // 条件：每个相邻间隔有独立的连接符（且/或/同组），从左到右求值。
            InsetGroupedSection(header = stringResource(R.string.section_conditions)) {
                rule.conditions.forEachIndexed { i, condition ->
                    if (i > 0) {
                        val gap = rule.conditionJoins.getOrNull(i - 1) ?: GapOp.AND
                        ConditionGapChip(gap) { vm.setGap(i - 1, it) }
                    }
                    val (ic, col) = ComponentVisuals.of(condition)
                    IOSRow(
                        title = Localize.summary(condition),
                        icon = ic,
                        iconColor = col,
                        onClick = { editingCondition = condition },
                    )
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
    // 内容较多的动作改用全屏二级界面；其余动作仍用对话框。
    when (editingAction) {
        is Action.WebhookAction ->
            WebhookEditorScreen(editingAction, onActionSave, onActionDelete, onActionDismiss)
        is Action.LaunchAppAction ->
            LaunchAppEditorScreen(editingAction, onActionSave, onActionDelete, onActionDismiss)
        is Action.RunMacroAction ->
            MacroEditorScreen(editingAction, onActionSave, onActionDelete, onActionDismiss)
        else ->
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

/**
 * 两个相邻条件之间的连接符「且 / 或 / 同组」，独立可改，带编辑图标可点击。
 * 菜单提供「且 / 或 / 删除」；删除即设为「同组」（无操作符，按或求值）。
 */
@Composable
private fun ConditionGapChip(gap: GapOp, onSelect: (GapOp) -> Unit) {
    var open by androidx.compose.runtime.remember { mutableStateOf(false) }
    val (label, grouped) = when (gap) {
        GapOp.AND -> stringResource(R.string.cond_join_and) to false
        GapOp.OR -> stringResource(R.string.cond_join_or) to false
        GapOp.GROUP -> stringResource(R.string.cond_join_group) to true
    }
    val tint = if (grouped) MaterialTheme.colorScheme.onSurfaceVariant else IOSColors.Blue
    Box(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        // 内层 Box 紧裹连接词，作为下拉菜单的锚点，使菜单贴着它弹出（而非整行左侧）。
        Box {
            androidx.compose.foundation.layout.Row(
                Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .clickable { open = true }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
                androidx.compose.material3.Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.cond_logic_label),
                    tint = tint,
                    modifier = Modifier.size(11.dp),
                )
            }
            androidx.compose.material3.DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .cardFrost(),
                containerColor = Color.Transparent,
            ) {
                @Composable
                fun item(textRes: Int, value: GapOp) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(textRes),
                                color = if (value == gap) IOSColors.Blue else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = { onSelect(value); open = false },
                    )
                }
                item(R.string.cond_join_and, GapOp.AND)
                item(R.string.cond_join_or, GapOp.OR)
                // 「删除」连接符 = 设为同组（无操作符）。
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete), color = IOSColors.Red) },
                    onClick = { onSelect(GapOp.GROUP); open = false },
                )
            }
        }
    }
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
    // 「此刻」的设备状态：判断规则条件当前是否成立（如是否在所选时段内）。
    // 内容匹配只看应用+触发器；命中与否还要看条件——否则过了时段仍会误显示为命中。
    val device by androidx.compose.runtime.produceState<DeviceContext?>(null, rule) {
        val needsHp = rule.conditions.any { it is Condition.HeadphonesCondition }
        val needsWifi = rule.conditions.any { it is Condition.WifiCondition }
        val needsLoc = rule.conditions.any { it is Condition.LocationCondition }
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            DeviceState.sample(context, needsHp, needsWifi, needsLoc)
        }
    }
    val activeNow = device?.let { engine.conditionsActiveNow(rule, it) } ?: true
    InsetGroupedSection(
        header = stringResource(R.string.preview_title),
        footer = when {
            allMatches.isEmpty() -> stringResource(R.string.preview_hint)
            activeNow -> stringResource(R.string.preview_match_count, allMatches.size)
            // 触发器/内容匹配，但当前条件不满足（如已过所选时段）——此刻不会执行。
            else -> stringResource(R.string.preview_inactive_now, allMatches.size)
        },
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
