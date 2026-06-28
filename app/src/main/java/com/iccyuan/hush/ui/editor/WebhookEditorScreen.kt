@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.iccyuan.hush.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iccyuan.hush.R
import com.iccyuan.hush.data.model.Action
import com.iccyuan.hush.data.model.HttpMethod
import com.iccyuan.hush.data.model.KeyValue
import com.iccyuan.hush.data.model.WebhookBodyType
import com.iccyuan.hush.ui.common.EnumDropdown
import com.iccyuan.hush.ui.common.LabeledTextField
import com.iccyuan.hush.ui.components.GlassScaffold
import com.iccyuan.hush.ui.components.HairlineDivider
import com.iccyuan.hush.ui.components.InsetGroupedSection
import com.iccyuan.hush.ui.theme.IOSColors

/** WebhookBodyType 的友好显示名（接近 Postman 的写法）。 */
private fun bodyTypeLabel(t: WebhookBodyType): String = when (t) {
    WebhookBodyType.JSON -> "JSON (application/json)"
    WebhookBodyType.TEXT -> "Text (text/plain)"
    WebhookBodyType.XML -> "XML (application/xml)"
}

/** 各请求体类型的默认示例模板。 */
private fun defaultBody(t: WebhookBodyType): String = when (t) {
    WebhookBodyType.JSON -> "{\"app\":\"{app}\",\"title\":\"{title}\",\"text\":\"{text}\"}"
    WebhookBodyType.TEXT -> "{app}: {title} {text}"
    WebhookBodyType.XML ->
        "<notification>\n  <app>{app}</app>\n  <title>{title}</title>\n  <text>{text}</text>\n</notification>"
}

/** 当前请求体是否仍是某个类型的默认模板（或空）——是则切换类型时可安全替换为新类型的默认。 */
private fun isDefaultBody(body: String): Boolean =
    body.isBlank() || WebhookBodyType.entries.any { body == defaultBody(it) }

/** 尝试把 JSON 美化缩进；不是合法 JSON 时返回 null（保持原样）。 */
private fun formatJson(s: String): String? = try {
    val t = s.trim()
    when {
        t.startsWith("{") -> org.json.JSONObject(t).toString(2)
        t.startsWith("[") -> org.json.JSONArray(t).toString(2)
        else -> null
    }
} catch (_: Exception) {
    null
}

/**
 * 全屏的 Webhook 编辑（类 Postman 的二级界面）：URL + 方法、查询参数、请求头，
 * POST 还可选请求体类型与内容。比对话框空间充裕、更好操作。
 */
@Composable
fun WebhookEditorScreen(
    action: Action.WebhookAction,
    onSave: (Action) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(action.id) { mutableStateOf(action) }

    GlassScaffold(
        title = stringResource(R.string.cat_act_webhook),
        onBack = onDismiss,
        actions = {
            TextButton(onClick = { onSave(draft) }) { Text(stringResource(R.string.save)) }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(2.dp))

            // 请求：URL + 方法（仅 GET / POST）。无分区标题——字段自带标签，不重复。
            InsetGroupedSection {
                Column(Modifier.padding(12.dp)) {
                    val urlError = if (draft.url.isNotBlank() &&
                        !android.util.Patterns.WEB_URL.matcher(draft.url).matches()
                    ) stringResource(R.string.err_invalid_url) else null
                    LabeledTextField(stringResource(R.string.url), draft.url, error = urlError) {
                        draft = draft.copy(url = it)
                    }
                    Spacer(Modifier.height(8.dp))
                    EnumDropdown(
                        label = stringResource(R.string.method),
                        options = listOf(HttpMethod.GET, HttpMethod.POST),
                        selected = if (draft.method == HttpMethod.POST) HttpMethod.POST else HttpMethod.GET,
                        optionLabel = { it.name },
                        onSelected = { draft = draft.copy(method = it) },
                    )
                }
            }

            // 请求头（放在查询参数之前）。无标题——「添加请求头」按钮已标明。
            KeyValueSection(
                addLabel = stringResource(R.string.add_header),
                items = draft.headers,
                onChange = { draft = draft.copy(headers = it) },
            )

            // 查询参数（拼到 URL，GET/POST 都可用）。
            KeyValueSection(
                addLabel = stringResource(R.string.add_param),
                items = draft.queryParams,
                onChange = { draft = draft.copy(queryParams = it) },
            )

            // 请求体（仅 POST）：类型 + 内容 + 操作行（说明 / 格式化）。
            if (draft.method == HttpMethod.POST) {
                var helpOpen by remember { mutableStateOf(false) }
                InsetGroupedSection {
                    Column(Modifier.padding(12.dp)) {
                        EnumDropdown(
                            label = stringResource(R.string.body_type),
                            options = WebhookBodyType.entries,
                            selected = draft.bodyType,
                            optionLabel = { bodyTypeLabel(it) },
                            onSelected = { newType ->
                                // 切换类型时，若用户未自定义请求体（仍是某默认或为空），替换为新类型的默认模板。
                                draft = draft.copy(
                                    bodyType = newType,
                                    bodyTemplate = if (isDefaultBody(draft.bodyTemplate)) {
                                        defaultBody(newType)
                                    } else {
                                        draft.bodyTemplate
                                    },
                                )
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        LabeledTextField(
                            stringResource(R.string.body_template),
                            draft.bodyTemplate,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                            singleLine = false,
                        ) { draft = draft.copy(bodyTemplate = it) }
                        // 操作行：左侧「使用说明」可展开，右侧「格式化」（仅 JSON）。
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.clickable { helpOpen = !helpOpen },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    if (helpOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = IOSColors.Blue,
                                )
                                Text(
                                    stringResource(R.string.body_help_title),
                                    color = IOSColors.Blue,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            if (draft.bodyType == WebhookBodyType.JSON) {
                                TextButton(onClick = {
                                    formatJson(draft.bodyTemplate)?.let { draft = draft.copy(bodyTemplate = it) }
                                }) { Text(stringResource(R.string.format_json)) }
                            }
                        }
                        if (helpOpen) {
                            Text(
                                stringResource(R.string.body_help_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.webhook_get_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            InsetGroupedSection {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onDelete() }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.delete), color = IOSColors.Red)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 可增删的键值对列表分区（用于查询参数与请求头）。值支持模板占位符。无标题——靠「添加…」按钮标识。 */
@Composable
private fun KeyValueSection(
    addLabel: String,
    items: List<KeyValue>,
    onChange: (List<KeyValue>) -> Unit,
) {
    InsetGroupedSection {
        items.forEachIndexed { i, kv ->
            if (i > 0) HairlineDivider(startInset = 16.dp)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LabeledTextField(
                    stringResource(R.string.header_name), kv.name, modifier = Modifier.weight(1f),
                ) { v -> onChange(items.mapAt(i) { it.copy(name = v) }) }
                LabeledTextField(
                    stringResource(R.string.header_value), kv.value, modifier = Modifier.weight(1.3f),
                ) { v -> onChange(items.mapAt(i) { it.copy(value = v) }) }
                IconButton(onClick = { onChange(items.filterIndexed { j, _ -> j != i }) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.delete))
                }
            }
        }
        if (items.isNotEmpty()) HairlineDivider(startInset = 16.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onChange(items + KeyValue()) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = IOSColors.Blue)
            Text(addLabel, style = MaterialTheme.typography.bodyMedium, color = IOSColors.Blue)
        }
    }
}

private fun <T> List<T>.mapAt(index: Int, transform: (T) -> T): List<T> =
    mapIndexed { i, item -> if (i == index) transform(item) else item }
