package com.buzzkill.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Scrim = Color(0x66000000)

/** 无水波纹的可点击修饰符（用于遮罩层和吞噬点击的面板）。 */
@Composable
private fun Modifier.composedClickable(onTap: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onTap)
}

/** 在合成中渲染的居中毛玻璃对话框（因此它会模糊背景）。 */
@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haze = LocalHazeState.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Scrim)
            .composedClickable(onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(28.dp)
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(22.dp))
                .glassPanel(haze)
                .composedClickable {} // 吞噬点击，以免触发关闭
                .padding(20.dp),
            content = content,
        )
    }
}

/** 在合成中渲染的底部毛玻璃面板。 */
@Composable
fun GlassSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haze = LocalHazeState.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Scrim)
            .composedClickable(onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .glassPanel(haze)
                .composedClickable {}
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    .padding(vertical = 2.5.dp),
            )
            content()
        }
    }
}

/** 如果有可用的模糊源则使用毛玻璃材质，否则使用不透明的抬升表面。 */
@Composable
private fun Modifier.glassPanel(haze: dev.chrisbanes.haze.HazeState?): Modifier =
    if (haze != null) this.frostedOverlay(haze)
    else this.background(MaterialTheme.colorScheme.surface)

/** 对话框按钮的尾部行（次要按钮在左，确认按钮在右）。 */
@Composable
fun DialogActions(
    confirmText: String,
    onConfirm: () -> Unit,
    secondaryText: String,
    onSecondary: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.TextButton(onClick = onSecondary) {
            androidx.compose.material3.Text(secondaryText)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        androidx.compose.material3.TextButton(onClick = onConfirm) {
            androidx.compose.material3.Text(confirmText)
        }
    }
}
