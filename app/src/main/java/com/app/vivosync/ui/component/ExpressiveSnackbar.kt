package com.app.vivosync.ui.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.vivosync.ui.theme.VivoSyncTheme

/**
 * Material 3 Expressive 风格悬浮 SnackbarHost 容器
 *
 * 规范要点对齐：
 * 1. 形状（Shape）：采用 MD3 Expressive 推荐的柔和大圆角卡片（RoundedCornerShape 16.dp）；
 * 2. 色彩（Color）：浅色与深色模式统一采用表面色阶容器（surfaceContainerHigh / onSurface / primary），
 *    避免深色模式下经典 M3 强行反转产生刺眼的白底“闪光弹效应”，同时在浅色模式下呈现现代质感的悬浮层；
 * 3. 边距与位置（Placement）：左右各保留 16.dp 悬浮呼吸边距，支持自定义底部避让；
 * 4. 交互：支持 SwipeToDismissBox 手势平滑滑动消除。
 *
 * @param hostState 管理 Snackbar 状态的 SnackbarHostState
 * @param modifier 外部修饰符
 * @param bottomPadding 底部避让间距，默认 16.dp
 */
@Composable
fun ExpressiveSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 16.dp
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
    ) { data ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = bottomPadding),
            contentAlignment = Alignment.BottomCenter
        ) {
            val dismissState = rememberSwipeToDismissBoxState()
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {},
                onDismiss = {
                    data.dismiss()
                }
            ) {
                ExpressiveSnackbarContent(data = data)
            }
        }
    }
}

@Composable
fun ExpressiveSnackbarContent(
    data: SnackbarData,
    modifier: Modifier = Modifier
) {
    Snackbar(
        snackbarData = data,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        actionColor = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

/**
 * M3 Expressive Snackbar - 浅色模式预览
 */
@Preview(name = "Expressive Snackbar - Light", showBackground = true)
@Composable
fun ExpressiveSnackbarLightPreview() {
    VivoSyncTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            ExpressiveSnackbarContent(
                data = object : SnackbarData {
                    override val visuals = object : SnackbarVisuals {
                        override val message: String = "日历同步成功"
                        override val actionLabel: String = "撤销"
                        override val withDismissAction: Boolean = false
                        override val duration: SnackbarDuration = SnackbarDuration.Short
                    }
                    override fun dismiss() {}
                    override fun performAction() {}
                }
            )
        }
    }
}

/**
 * M3 Expressive Snackbar - 深色模式预览
 */
@Preview(name = "Expressive Snackbar - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ExpressiveSnackbarDarkPreview() {
    VivoSyncTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            ExpressiveSnackbarContent(
                data = object : SnackbarData {
                    override val visuals = object : SnackbarVisuals {
                        override val message: String = "日历同步成功"
                        override val actionLabel: String = "撤销"
                        override val withDismissAction: Boolean = false
                        override val duration: SnackbarDuration = SnackbarDuration.Short
                    }
                    override fun dismiss() {}
                    override fun performAction() {}
                }
            )
        }
    }
}
