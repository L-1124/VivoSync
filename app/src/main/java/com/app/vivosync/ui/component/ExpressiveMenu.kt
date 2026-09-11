package com.app.vivosync.ui.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset

/**
 * 基于按压坐标锚定的 Material 3 Expressive 悬浮菜单
 *
 * 区别于传统的固定卡片右上角或屏幕边缘弹出的菜单，本组件将弹窗锚定在用户实际手指触摸按压的相对坐标上，
 * 呈现贴近手指、自然发散的灵动交互体验。
 *
 * @param expanded 菜单是否展开
 * @param onDismissRequest 关闭菜单回调
 * @param anchorOffset 手指按压触摸坐标的相对偏移量（通过 [trackPressPosition] 捕获）
 * @param content 菜单项内容，运行在具备平滑滚动特性的 [DropdownMenuGroup] 作用域内
 */
@Composable
fun OffsetAnchoredExpressiveMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorOffset: IntOffset = IntOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(Constraints())
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
            layout(width, 0) {
                placeable.place(anchorOffset.x, anchorOffset.y)
            }
        }
    ) {
        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
        ) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(index = 0, count = 1),
                modifier = Modifier.verticalScroll(rememberScrollState()),
                content = content,
            )
        }
    }
}

/**
 * 监听并捕获用户在组件上的首次触碰位置
 *
 * 与 [OffsetAnchoredExpressiveMenu] 配合使用，用于记录用户点击该行时的绝对/相对坐标，
 * 从而让弹出菜单在按压手势原位浮现。
 *
 * @param onPress 用户按下手指时的相对坐标回调
 */
fun Modifier.trackPressPosition(onPress: (Offset) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onPress(down.position)
    }
}
