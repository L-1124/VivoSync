package com.app.vivosync.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.app.vivosync.R

/**
 * Material 3 Expressive 灵动开关组件
 *
 * 遵循 Material 3 Expressive 规范设计，滑块内嵌状态图标（开启显示对勾，关闭显示叉号），
 * 并提供富有表现力的色调层级与无缝的交互反馈。
 *
 * @param checked 当前是否处于选中（开启）状态
 * @param onCheckedChange 状态变更回调。若为 null，则由外部父级容器统一接管点击与手势
 * @param modifier 外部修饰符
 * @param thumbContent 自定义滑块内微图标内容。为 null 且 [showThumbIcon] 为 true 时使用默认图标
 * @param enabled 是否允许交互
 * @param colors 开关色彩方案，默认采用符合 Expressive 规范的 [expressiveSwitchColors]
 * @param interactionSource 交互状态源，便于父级组件同步水波纹与高亮反馈
 * @param showThumbIcon 是否在滑块中心展示微图标
 */
@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = expressiveSwitchColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    showThumbIcon: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbContent = thumbContent ?: if (showThumbIcon && (checked || enabled)) {
            {
                Icon(
                    painter = painterResource(id = if (checked) R.drawable.ic_check else R.drawable.ic_close),
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        } else null,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource
    )
}

/**
 * 构建符合 Material 3 Expressive 规范的开关色彩体系
 *
 * 优化滑块内图标与各容器色（Surface Container Highest）的对比度，
 * 确保在深色模式与动态色彩环境下的清晰辨识度与层次感。
 */
@Composable
fun expressiveSwitchColors(
    checkedIconColor: Color = MaterialTheme.colorScheme.primary,
    uncheckedIconColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    disabledCheckedThumbColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.38f),
    disabledCheckedTrackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    disabledCheckedIconColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    disabledUncheckedThumbColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
    disabledUncheckedTrackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.12f),
    disabledUncheckedBorderColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    disabledUncheckedIconColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
): SwitchColors = SwitchDefaults.colors(
    checkedIconColor = checkedIconColor,
    uncheckedIconColor = uncheckedIconColor,
    disabledCheckedThumbColor = disabledCheckedThumbColor,
    disabledCheckedTrackColor = disabledCheckedTrackColor,
    disabledCheckedIconColor = disabledCheckedIconColor,
    disabledUncheckedThumbColor = disabledUncheckedThumbColor,
    disabledUncheckedTrackColor = disabledUncheckedTrackColor,
    disabledUncheckedBorderColor = disabledUncheckedBorderColor,
    disabledUncheckedIconColor = disabledUncheckedIconColor,
)
