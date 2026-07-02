@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.iccyuan.hush.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.iccyuan.hush.R
import com.iccyuan.hush.data.AppInfo
import com.iccyuan.hush.data.InstalledApps
import com.iccyuan.hush.ui.common.LabeledTextField
import com.iccyuan.hush.ui.components.GlassScaffold
import com.iccyuan.hush.ui.components.IOSSwitch
import com.iccyuan.hush.ui.components.cardFrost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 全屏应用选择器：可搜索的方形网格，显示图标 + 名称，并带系统应用开关。 */
@Composable
fun AppPickerScreen(
    initiallySelected: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val selected = remember { mutableStateListOf<String>().apply { addAll(initiallySelected) } }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    val apps by produceState<List<AppInfo>?>(initialValue = null, showSystem) {
        value = null
        value = withContext(Dispatchers.IO) { InstalledApps.load(context, showSystem) }
    }

    GlassScaffold(
        title = stringResourceSelect(),
        onBack = onDismiss,
        actions = {
            TextButton(onClick = { onConfirm(selected.toList()) }) {
                Text(androidx.compose.ui.res.stringResource(R.string.done))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(8.dp))
                LabeledTextField(
                    androidx.compose.ui.res.stringResource(R.string.search), query
                ) { query = it }
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        androidx.compose.ui.res.stringResource(R.string.show_system_apps),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    IOSSwitch(showSystem) { showSystem = it }
                }
                Spacer(Modifier.height(4.dp))
            }

            val list = apps
            if (list == null) {
                LoadingSkeleton()
            } else {
                val filtered = remember(query, list) {
                    if (query.isBlank()) list
                    else list.filter {
                        it.label.contains(query, true) || it.packageName.contains(query, true)
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(78.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 令牌「包名@用户id」唯一标识本体/分身，避免重复键崩溃、并使二者可分别勾选。
                    items(filtered, key = { it.token }) { app ->
                        // 兼容旧规则里保存的裸包名（视作已选中本体）。
                        val isSel = selected.contains(app.token) ||
                            (!app.isClone && selected.contains(app.packageName))
                        AppGridItem(
                            app = app,
                            selected = isSel,
                            onToggle = {
                                if (isSel) {
                                    selected.remove(app.token)
                                    if (!app.isClone) selected.remove(app.packageName)
                                } else {
                                    selected.add(app.token)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun stringResourceSelect() = androidx.compose.ui.res.stringResource(R.string.select_apps)

@Composable
private fun AppGridItem(app: AppInfo, selected: Boolean, onToggle: () -> Unit) {
    val bitmap = remember(app.packageName) {
        runCatching { app.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }
    val primary = MaterialTheme.colorScheme.primary
    // 未选中时不显示边框（看起来太重）；仅在选中时显示淡淡的着色玻璃质感，
    // 并加一圈细细的强调色描边。
    val borderColor by androidx.compose.animation.animateColorAsState(
        if (selected) primary.copy(alpha = 0.55f) else Color.Transparent, label = "border"
    )
    val tint by androidx.compose.animation.animateColorAsState(
        if (selected) primary.copy(alpha = 0.16f) else Color.Transparent, label = "tint"
    )
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .cardFrost()
            .background(tint)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .padding(8.dp),
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = app.label, modifier = Modifier.size(36.dp))
            } else {
                Icon(Icons.Filled.Android, contentDescription = null, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(6.dp))
            val displayLabel = if (app.isClone)
                "${app.label} ${androidx.compose.ui.res.stringResource(R.string.app_clone_suffix)}"
            else app.label
            Text(
                displayLabel,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold
                else androidx.compose.ui.text.font.FontWeight.Normal,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        // 选中标记：角落里一个简单的对勾（不带圆圈）。
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
            )
        }
    }
}

@Composable
private fun LoadingSkeleton() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(800),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(78.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(12) {
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.35f)),
            )
        }
    }
}
