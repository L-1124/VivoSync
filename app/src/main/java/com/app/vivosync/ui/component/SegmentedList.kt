package com.app.vivosync.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import com.app.vivosync.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 分段列表项形状的 CompositionLocal，用于在容器层级向下传递自适应圆角策略
 */
val LocalListItemShapes = compositionLocalOf<ListItemShapes?> { null }

/**
 * 分段外侧的大圆角（首项顶部与末项底部）
 */
private val SegmentedOuterRadius = 16.dp

/**
 * 分段内侧的小圆角（各行接缝处）
 */
private val SegmentedInnerRadius = 4.dp

/**
 * 物理弹性动画刚度系数，提供灵动爽脆的形变与位移动画
 */
private const val SegmentedSpringStiffness = 800f

/**
 * 物理弹性动画阻尼比，确保无震荡、平滑迅速衰减
 */
private const val SegmentedSpringDamping = 0.9f

/**
 * DSL 作用域注解，限制 SegmentedColumn 的子级调用作用域
 */
@DslMarker
annotation class SegmentedColumnDsl

/**
 * 构建 Material 3 Expressive 分段列表项的默认色调
 *
 * 采用表面亮色（surfaceBright）作为卡片底色，配合页面背景 surfaceContainer 呈现高质感岛屿卡片对比。
 */
@Composable
fun defaultSegmentedColors(
    containerColor: Color = colorScheme.surfaceBright,
    disabledContainerColor: Color = colorScheme.surfaceBright,
    supportingContentColor: Color = colorScheme.onSurfaceVariant
): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = containerColor,
    disabledContainerColor = disabledContainerColor,
    supportingContentColor = supportingContentColor
)

/**
 * 根据项目索引及总项数计算分段自适应形状
 *
 * 当分组内仅存在单个项目时，自动退化为全大圆角（16dp）以保持卡片整体视觉完整。
 */
@Composable
private fun defaultSingleSegmentedShape(index: Int, count: Int): ListItemShapes {
    val base = ListItemDefaults.segmentedShapes(index, count)
    return if (count == 1) {
        base.copy(shape = MaterialTheme.shapes.large)
    } else {
        base
    }
}

/**
 * Material 3 Expressive 分段列表容器（列表重载）
 *
 * 将传入的 Composable 项目按顺序封装为分段卡片组，首尾项目自适应外大圆角，中间项目使用接缝小圆角。
 *
 * @param modifier 外部修饰符
 * @param title 分组标题（若非空，将在卡片上方展示灵动的分类小标题）
 * @param visibleLen 显式指定的可见项数量，默认根据 content 列表大小自动计算
 * @param content 子级 Composable 列表
 */
@Composable
fun SegmentedColumn(
    modifier: Modifier = Modifier,
    title: String = "",
    visibleLen: Int = 0,
    content: List<@Composable () -> Unit>,
) {
    if (content.isEmpty()) return

    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            content.forEachIndexed { index, itemContent ->
                CompositionLocalProvider(
                    LocalListItemShapes provides defaultSingleSegmentedShape(
                        index = index,
                        count = if (visibleLen > 0) visibleLen else content.size
                    ),
                ) {
                    itemContent()
                }
            }
        }
    }
}

/**
 * SegmentedColumn DSL 作用域构造器，用于支持动态条件显示与带键跟踪的子项
 */
@SegmentedColumnDsl
class SegmentedColumnScope {
    internal data class Entry(
        val key: Any?,
        val visible: Boolean,
        val content: @Composable () -> Unit,
    )

    internal val entries = mutableListOf<Entry>()

    /**
     * 向分段容器中添加一个列表项
     *
     * @param key 唯一标识键，用于动画状态跟踪
     * @param visible 是否可见，支持条件隐藏并驱动流畅的高度弹簧退场动效
     * @param content 项目 Composable 内容
     */
    fun item(
        key: Any? = null,
        visible: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        entries.add(Entry(key ?: entries.size, visible, content))
    }
}

/**
 * Material 3 Expressive 动态灵动分段列表容器（DSL 重载）
 *
 * 支持通过 [SegmentedColumnScope.item] 动态加入带有显隐动画的列表项。
 * 当子项动态增删时，首尾圆角与高度将通过 Spring 物理弹簧模型自然形变，提供极致流畅的视觉交互。
 *
 * @param modifier 外部修饰符
 * @param title 分组标题
 * @param content DSL 作用域内容块
 */
@Composable
fun SegmentedColumn(
    modifier: Modifier = Modifier,
    title: String = "",
    content: SegmentedColumnScope.() -> Unit,
) {
    val entries = SegmentedColumnScope().apply(content).entries
    if (entries.isEmpty()) return

    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }

        val floatSpring = spring<Float>(SegmentedSpringDamping, SegmentedSpringStiffness)
        val dpSpring = spring<Dp>(SegmentedSpringDamping, SegmentedSpringStiffness)

        val progresses = entries.mapIndexed { index, entry ->
            key(entry.key ?: index) {
                animateFloatAsState(
                    targetValue = if (entry.visible) 1f else 0f,
                    animationSpec = floatSpring,
                    label = "SegmentedProgress"
                )
            }
        }

        val firstVisible = entries.indexOfFirst { it.visible }
        val lastVisible = entries.indexOfLast { it.visible }

        Layout(
            content = {
                entries.forEachIndexed { index, entry ->
                    key(entry.key ?: index) {
                        val isFirst = if (firstVisible == -1) index == 0 else index == firstVisible
                        val isLast = if (lastVisible == -1) index == entries.lastIndex else index == lastVisible

                        val topRadius by animateDpAsState(
                            if (isFirst) SegmentedOuterRadius else SegmentedInnerRadius,
                            dpSpring, label = "SegmentedTopRadius"
                        )
                        val bottomRadius by animateDpAsState(
                            if (isLast) SegmentedOuterRadius else SegmentedInnerRadius,
                            dpSpring, label = "SegmentedBottomRadius"
                        )
                        val gap by animateDpAsState(
                            if (isFirst) 0.dp else ListItemDefaults.SegmentedGap,
                            dpSpring, label = "SegmentedGap"
                        )

                        val shape = RoundedCornerShape(
                            topStart = topRadius, topEnd = topRadius,
                            bottomStart = bottomRadius, bottomEnd = bottomRadius
                        )

                        Box(
                            modifier = Modifier
                                .zIndex(if (entry.visible) (entries.size - index).toFloat() else -index.toFloat())
                                .graphicsLayer {
                                    val progress = progresses[index].value.coerceAtLeast(0f)
                                    clip = true
                                    this.shape = object : Shape {
                                        override fun createOutline(
                                            size: Size,
                                            layoutDirection: LayoutDirection,
                                            density: Density,
                                        ): Outline = Outline.Rectangle(Rect(0f, 0f, size.width, size.height * progress))
                                    }
                                    alpha = (progress * 1.5f).coerceIn(0f, 1f)
                                }
                        ) {
                            CompositionLocalProvider(
                                LocalListItemShapes provides ListItemDefaults.segmentedShapes(0, 1).copy(shape = shape)
                            ) {
                                Column(modifier = Modifier.padding(top = gap)) {
                                    entry.content()
                                }
                            }
                        }
                    }
                }
            }
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints) }
            val positions = IntArray(placeables.size)
            var y = 0f
            placeables.forEachIndexed { index, placeable ->
                positions[index] = y.roundToInt()
                y += placeable.height * progresses[index].value.coerceAtLeast(0f)
            }
            layout(constraints.maxWidth, y.roundToInt().coerceAtLeast(0)) {
                placeables.forEachIndexed { index, placeable ->
                    placeable.placeRelative(0, positions[index])
                }
            }
        }
    }
}

/**
 * 分段基础项包装容器
 */
@Composable
fun SegmentedItem(
    index: Int,
    count: Int,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalListItemShapes provides defaultSingleSegmentedShape(index, count),
    ) {
        content()
    }
}

/**
 * 带有分段形状裁剪的独立容器卡片
 */
@Composable
fun SegmentedItemContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorScheme.surfaceBright,
        shape = shapes.shape,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

/**
 * Material 3 Expressive 基础分段列表项
 */
@Composable
fun SegmentedListItem(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: ListItemColors = defaultSegmentedColors(),
    interactionSource: MutableInteractionSource? = null,
    headlineContent: @Composable () -> Unit,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    SegmentedListItem(
        onClick = onClick ?: {},
        onLongClick = onLongClick,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
        modifier = modifier,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        verticalAlignment = Alignment.CenterVertically,
        content = headlineContent
    )
}

/**
 * Material 3 Expressive 带开关/选择框状态的分段列表项
 */
@Composable
fun SegmentedListItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ListItemColors = defaultSegmentedColors(),
    interactionSource: MutableInteractionSource? = null,
    headlineContent: @Composable () -> Unit,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        verticalAlignment = Alignment.CenterVertically,
        onLongClick = onLongClick,
        content = headlineContent
    )
}

/**
 * Material 3 Expressive 带单选状态的分段列表项
 */
@Composable
fun SegmentedListItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ListItemColors = defaultSegmentedColors(),
    interactionSource: MutableInteractionSource? = null,
    headlineContent: @Composable () -> Unit,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = LocalListItemShapes.current ?: ListItemDefaults.segmentedShapes(0, 1),
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        verticalAlignment = Alignment.CenterVertically,
        onLongClick = onLongClick,
        content = headlineContent
    )
}

/**
 * Material 3 Expressive 分段开关项 (SegmentedSwitchItem)
 *
 * 融合触感震动反馈（VirtualKey）、自适应圆角与灵动开关，支持前置图标资源（Vector 或 Drawable）、
 * 主标题与详细辅助描述文字。点击整行任意区域均可直接触发平滑切换。
 *
 * @param title 选项主标题
 * @param checked 当前开关状态
 * @param onCheckedChange 开关状态变更回调
 * @param modifier 外部修饰符
 * @param icon 前置图标 ImageVector（可选）
 * @param iconResId 前置图标 Drawable 资源 ID（可选）
 * @param summary 选项下方的辅助解释文字（可选）
 * @param enabled 是否处于可交互状态
 * @param colors 配色规范
 */
@Composable
fun SegmentedSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconResId: Int? = null,
    summary: String? = null,
    enabled: Boolean = true,
    colors: ListItemColors = defaultSegmentedColors(),
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    SegmentedListItem(
        modifier = modifier,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onCheckedChange(!checked)
        },
        enabled = enabled,
        interactionSource = interactionSource,
        colors = colors,
        headlineContent = { Text(title) },
        leadingContent = when {
            icon != null -> { { Icon(icon, title) } }
            iconResId != null -> { { Icon(painterResource(iconResId), title) } }
            else -> null
        },
        trailingContent = {
            ExpressiveSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
                interactionSource = interactionSource,
            )
        },
        supportingContent = summary?.let { { Text(it) } }
    )
}

/**
 * Material 3 Expressive 分段下拉单选项 (泛型重载)
 *
 * 右侧展示当前选中文本，点击整行后在按压坐标原位唤起带有 Checkmark 选中标记的 Expressive 弹窗。
 *
 * @param title 选项主标题
 * @param items 下拉可选数据列表
 * @param selectedIndex 当前选中的数据索引
 * @param onItemSelected 数据选中索引变更回调
 * @param modifier 外部修饰符
 * @param icon 前置图标 ImageVector（可选）
 * @param iconResId 前置图标 Drawable 资源 ID（可选）
 * @param summary 选项下方的辅助解释文字（可选）
 * @param enabled 是否处于可用交互状态
 * @param colors 配色规范
 * @param onClick 点击事件的前置监听拦截（可选）
 * @param itemLabel 数据项显示文本转换函数
 * @param itemSummary 数据项辅助详情转换函数（可选，展示于菜单项文本下方）
 */
@Composable
fun <T> SegmentedDropdownItem(
    title: String,
    items: List<T>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconResId: Int? = null,
    summary: String? = null,
    enabled: Boolean = true,
    colors: ListItemColors = defaultSegmentedColors(),
    onClick: (() -> Unit)? = null,
    itemLabel: @Composable (T) -> String = { it.toString() },
    itemSummary: (@Composable (T) -> String)? = null,
) {
    val haptic = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    var anchorOffset by remember { mutableStateOf(IntOffset.Zero) }

    val hasItems = items.isNotEmpty()
    val safeIndex = if (hasItems) {
        selectedIndex.coerceIn(0, items.lastIndex)
    } else {
        -1
    }

    Box(modifier = modifier.trackPressPosition { anchorOffset = it.round() }) {
        SegmentedListItem(
            onClick = if (enabled) {
                {
                    onClick?.invoke()
                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    expanded = true
                }
            } else null,
            enabled = enabled,
            colors = colors,
            leadingContent = when {
                icon != null -> { { Icon(icon, title) } }
                iconResId != null -> { { Icon(painterResource(iconResId), title) } }
                else -> null
            },
            headlineContent = { Text(text = title) },
            supportingContent = summary?.let { { Text(it) } },
            trailingContent = {
                Text(
                    text = if (hasItems && safeIndex >= 0) itemLabel(items[safeIndex]) else "",
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(0.35f),
                    color = if (enabled) colorScheme.primary else colorScheme.onSurfaceVariant
                )
            }
        )
        OffsetAnchoredExpressiveMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            anchorOffset = anchorOffset,
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == safeIndex
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(itemLabel(item))
                            itemSummary?.invoke(item)?.let { subText ->
                                if (subText.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    selected = isSelected,
                    onClick = {
                        if (index in items.indices) {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onItemSelected(index)
                        }
                        expanded = false
                    },
                    colors = MenuDefaults.selectableItemColors(
                        selectedContainerColor = colorScheme.primaryContainer,
                        selectedTextColor = colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = colorScheme.primary,
                        textColor = colorScheme.onSurface,
                        leadingIconColor = colorScheme.onSurfaceVariant,
                    ),
                    shapes = MenuDefaults.itemShape(index = index, count = items.size),
                    selectedLeadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check),
                            contentDescription = null,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                        )
                    },
                )
            }
        }
    }
}

/**
 * Material 3 Expressive 分段下拉单选项 (String 列表便捷重载)
 */
@Composable
fun SegmentedDropdownItem(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconResId: Int? = null,
    summary: String? = null,
    enabled: Boolean = true,
    colors: ListItemColors = defaultSegmentedColors(),
    onClick: (() -> Unit)? = null,
) = SegmentedDropdownItem(
    title = title,
    items = items,
    selectedIndex = selectedIndex,
    onItemSelected = onItemSelected,
    modifier = modifier,
    icon = icon,
    iconResId = iconResId,
    summary = summary,
    enabled = enabled,
    colors = colors,
    onClick = onClick,
    itemLabel = { it },
    itemSummary = null
)

/**
 * Material 3 Expressive 分段单选项 (SegmentedRadioItem)
 */
@Composable
fun SegmentedRadioItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    colors: ListItemColors = defaultSegmentedColors(),
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current

    SegmentedListItem(
        modifier = modifier,
        selected = selected,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        },
        enabled = enabled,
        colors = colors,
        headlineContent = { Text(title) },
        leadingContent = {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled
            )
        },
        supportingContent = summary?.let { { Text(it) } }
    )
}

/**
 * Material 3 Expressive 分段多选复选框项 (SegmentedCheckboxItem)
 */
@Composable
fun SegmentedCheckboxItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    colors: ListItemColors = defaultSegmentedColors(),
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    SegmentedListItem(
        modifier = modifier,
        checked = checked,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onCheckedChange(it)
        },
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        headlineContent = { Text(title) },
        leadingContent = {
            Checkbox(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
                interactionSource = interactionSource,
                modifier = Modifier.size(24.dp)
            )
        },
        supportingContent = summary?.let { { Text(it) } }
    )
}

/**
 * Material 3 Expressive 分段输入框项 (SegmentedTextField)
 */
@Composable
fun SegmentedTextField(
    modifier: Modifier = Modifier,
    label: String = "",
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    colors: ListItemColors = defaultSegmentedColors(),
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    cursorBrush: Brush = SolidColor(colorScheme.primary),
    placeholder: @Composable (() -> Unit)? = { Text("-") },
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    isError: Boolean = false
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    SegmentedListItem(
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .focusRequester(focusRequester),
        colors = colors,
        onClick = { focusRequester.requestFocus() },
        leadingContent = leadingContent,
        supportingContent = supportingContent,
        trailingContent = trailingContent,
        headlineContent = {
            Column {
                if (label.isNotEmpty()) {
                    Text(text = label, color = if (isError) colorScheme.error else colors.contentColor)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (it.isFocused) {
                                coroutineScope.launch {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                    enabled = enabled,
                    readOnly = readOnly,
                    textStyle = textStyle.copy(
                        colors.supportingContentColor,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                    ),
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    minLines = minLines,
                    visualTransformation = visualTransformation,
                    onTextLayout = onTextLayout,
                    interactionSource = interactionSource,
                    cursorBrush = cursorBrush,
                    decorationBox = { innerTextField ->
                        if (value.isEmpty() && placeholder != null) {
                            Box(contentAlignment = Alignment.CenterStart) {
                                CompositionLocalProvider(
                                    LocalContentColor provides colors.supportingContentColor
                                ) {
                                    ProvideTextStyle(value = MaterialTheme.typography.bodyMedium) {
                                        placeholder()
                                    }
                                }
                            }
                        }
                        innerTextField()
                    }
                )
            }
        }
    )
}
