package com.app.vivosync.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.app.vivosync.VivoSyncBottomBar
import com.app.vivosync.ui.navigation.AppDestination
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.vivosync.R
import com.app.vivosync.model.CourseItem
import com.app.vivosync.model.VivoCourseCalendar
import com.app.vivosync.ui.state.VivoSyncUiState
import com.app.vivosync.ui.component.ExpressiveSnackbarContent
import com.app.vivosync.ui.component.ExpressiveSnackbarHost
import com.app.vivosync.ui.theme.VivoSyncTheme
import com.app.vivosync.ui.viewmodel.VivoSyncViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: VivoSyncViewModel,
    uiState: VivoSyncUiState,
    onPickIcsFile: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSchoolSelector: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    HomeScreenContent(
        uiState = uiState,
        onPickIcsFile = onPickIcsFile,
        onRequestPermission = onRequestPermission,
        onRefreshCalendars = { viewModel.refreshCalendars() },
        onOpenSchoolSelector = onOpenSchoolSelector,
        onDismissCoursesPreview = { viewModel.dismissCoursesPreview() },
        onSyncCoursesToVivoCalendar = { calId, courses -> viewModel.syncCoursesToVivoCalendar(calId, courses) },
        onSetShowNewCalendarDialog = { show -> viewModel.setShowNewCalendarDialog(show) },
        onCreateNewVivoCalendar = { name -> viewModel.createNewVivoCalendar(name) },
        onSwitchActiveVivoCalendar = { calId -> viewModel.switchActiveVivoCalendar(calId, autoOpenCalendar = true) },
        onOpenInVivoCalendar = { calId -> viewModel.openInVivoCalendar(calId) },
        onDeleteVivoCalendar = { calId -> viewModel.deleteVivoCalendar(calId) },
        onClearToastMessage = { viewModel.clearToastMessage() },
        modifier = modifier,
        bottomBar = bottomBar
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    uiState: VivoSyncUiState,
    onPickIcsFile: () -> Unit,
    onRequestPermission: () -> Unit,
    onRefreshCalendars: () -> Unit,
    onOpenSchoolSelector: () -> Unit,
    onDismissCoursesPreview: () -> Unit,
    onSyncCoursesToVivoCalendar: (Long?, List<CourseItem>) -> Unit,
    onSetShowNewCalendarDialog: (Boolean) -> Unit,
    onCreateNewVivoCalendar: (String) -> Unit,
    onSwitchActiveVivoCalendar: (Long) -> Unit,
    onOpenInVivoCalendar: (Long) -> Unit,
    onDeleteVivoCalendar: (Long) -> Unit,
    onClearToastMessage: () -> Unit,
    modifier: Modifier = Modifier,
    initialFabMenuExpanded: Boolean = false,
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: (@Composable () -> Unit)? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var calendarToDelete by remember { mutableStateOf<VivoCourseCalendar?>(null) }
    var newCalendarNameInput by remember { mutableStateOf("") }
    var showFabMenu by remember { mutableStateOf(initialFabMenuExpanded) }
    val listState = rememberLazyListState()

    // 当用户滑动课程表列表时，若菜单处于展开状态则自动收起
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && showFabMenu) {
            showFabMenu = false
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            onClearToastMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onRefreshCalendars) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_refresh),
                            contentDescription = stringResource(R.string.action_refresh)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = snackbarHost ?: { ExpressiveSnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            HomeScreenSpeedDialFab(
                expanded = showFabMenu,
                onExpandedChange = { showFabMenu = it },
                onOpenSchoolSelector = onOpenSchoolSelector,
                onPickIcsFile = onPickIcsFile,
                onNewCalendarClick = { onSetShowNewCalendarDialog(true) }
            )
        },
        bottomBar = bottomBar
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 权限提示卡片
                if (!uiState.hasCalendarPermission) {
                    item {
                        PermissionBannerCard(onRequestPermission = onRequestPermission)
                    }
                }

                // 核心卡片：系统课程表管理
                item {
                    VivoCalendarSectionHeader()
                }

                if (uiState.vivoCalendars.isEmpty()) {
                    item {
                        EmptyVivoCalendarsCard(
                            onNewCalendarClick = { onSetShowNewCalendarDialog(true) },
                            onPickIcsFile = onPickIcsFile
                        )
                    }
                } else {
                    items(uiState.vivoCalendars, key = { it.id }) { cal ->
                        VivoCalendarItemCard(
                            calendar = cal,
                            onSwitchActive = { onSwitchActiveVivoCalendar(cal.id) },
                            onOpenInCalendar = { onOpenInVivoCalendar(cal.id) },
                            onDelete = { calendarToDelete = cal }
                        )
                    }
                }
            }

            // 点击外部收起菜单
            if (showFabMenu) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showFabMenu = false
                        }
                )
            }
        }
    }


    // 课程提取预览与确认同步弹窗
    uiState.importedCoursesPreview?.let { courses ->
        CoursePreviewSyncDialog(
            courses = courses,
            startDate = uiState.importedStartDate,
            totalWeeks = uiState.importedConfig?.semesterTotalWeeks,
            vivoCalendars = uiState.vivoCalendars,
            isSyncing = uiState.isSyncing,
            onDismissRequest = onDismissCoursesPreview,
            onConfirmSync = { targetCalId ->
                onSyncCoursesToVivoCalendar(targetCalId, courses)
            }
        )
    }

    // 新建课程表 Dialog
    if (uiState.showNewCalendarDialog) {
        val defaultNewCalName = stringResource(R.string.dialog_create_calendar_label)
        AlertDialog(
            onDismissRequest = { onSetShowNewCalendarDialog(false) },
            title = { Text(stringResource(R.string.dialog_create_calendar_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.dialog_create_calendar_msg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newCalendarNameInput,
                        onValueChange = { newCalendarNameInput = it },
                        placeholder = { Text(stringResource(R.string.dialog_create_calendar_placeholder)) },
                        label = { Text(stringResource(R.string.dialog_create_calendar_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newCalendarNameInput.trim().ifBlank { defaultNewCalName }
                        onCreateNewVivoCalendar(name)
                        newCalendarNameInput = ""
                    }
                ) {
                    Text(stringResource(R.string.action_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { onSetShowNewCalendarDialog(false) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // 删除课程表确认 Dialog
    calendarToDelete?.let { cal ->
        AlertDialog(
            onDismissRequest = { calendarToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_calendar_title)) },
            text = { Text(stringResource(R.string.dialog_delete_calendar_msg, cal.displayName)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteVivoCalendar(cal.id)
                        calendarToDelete = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { calendarToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeScreenSpeedDialFab(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenSchoolSelector: () -> Unit,
    onPickIcsFile: () -> Unit,
    onNewCalendarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 返回键收起菜单
    BackHandler(enabled = expanded) {
        onExpandedChange(false)
    }

    // 预热/预加载图标资源，彻底避免在用户点击展开瞬间同步解析 XML
    val schoolPainter = painterResource(id = R.drawable.ic_school)
    val calendarPainter = painterResource(id = R.drawable.ic_calendar_month_outlined)
    val addPainter = painterResource(id = R.drawable.ic_add)

    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { onExpandedChange(!expanded) }
            ) {
                // 图标颜色平滑渐变：折叠时为 onPrimaryContainer，展开变深后平滑过渡为 onPrimary（高反亮色）
                val iconTint = lerp(
                    start = MaterialTheme.colorScheme.onPrimaryContainer,
                    stop = MaterialTheme.colorScheme.onPrimary,
                    fraction = checkedProgress
                )
                Icon(
                    painter = addPainter,
                    contentDescription = if (expanded) stringResource(R.string.fab_menu_collapse) else stringResource(R.string.fab_menu_expand),
                    tint = iconTint,
                    modifier = Modifier
                        .graphicsLayer {
                            rotationZ = checkedProgress * 135f
                        }
                )
            }
        },
        modifier = modifier
    ) {
        // Capsule 1: 从教务系统导入
        FloatingActionButtonMenuItem(
            onClick = {
                onExpandedChange(false)
                onOpenSchoolSelector()
            },
            text = {
                Text(
                    text = stringResource(R.string.fab_item_school),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)
                )
            },
            icon = {
                Icon(
                    painter = schoolPainter,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier.height(40.dp)
        )

        // Capsule 2: 导入 .ics 文件
        FloatingActionButtonMenuItem(
            onClick = {
                onExpandedChange(false)
                onPickIcsFile()
            },
            text = {
                Text(
                    text = stringResource(R.string.fab_item_ics),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)
                )
            },
            icon = {
                Icon(
                    painter = calendarPainter,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier.height(40.dp)
        )

        // Capsule 3: 新建空白课表
        FloatingActionButtonMenuItem(
            onClick = {
                onExpandedChange(false)
                onNewCalendarClick()
            },
            text = {
                Text(
                    text = stringResource(R.string.fab_item_new_calendar),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)
                )
            },
            icon = {
                Icon(
                    painter = addPainter,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier.height(40.dp)
        )
    }
}

@Composable
private fun VivoCalendarSectionHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.section_system_calendars),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun VivoCalendarItemCard(
    calendar: VivoCourseCalendar,
    onSwitchActive: () -> Unit,
    onOpenInCalendar: () -> Unit,
    onDelete: () -> Unit
) {
    val targetContainerColor = if (calendar.isActive) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 250),
        label = "cardContainerColor"
    )

    Card(
        onClick = onOpenInCalendar,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        CalendarCardContent(calendar, onSwitchActive, onOpenInCalendar, onDelete)
    }
}

@Composable
private fun CalendarCardContent(
    calendar: VivoCourseCalendar,
    onSwitchActive: () -> Unit,
    onOpenInCalendar: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧识别区：4.dp × 40.dp 圆角色条，使用 calendar.color（若未指定则跟随主题色 primary）
        val defaultStripeColor = MaterialTheme.colorScheme.primary
        val stripeColor = remember(calendar.color, defaultStripeColor) {
            if (calendar.color != 0) Color(calendar.color) else defaultStripeColor
        }
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(stripeColor)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 中间主体信息区：课表名称 + 课程数量
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = calendar.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.calendar_course_count, calendar.courseCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 右侧操作与状态区：Chip 状态切换 + 溢出菜单 (⋮)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (calendar.isActive) {
                SuggestionChip(
                    onClick = onOpenInCalendar,
                    label = {
                        Text(
                            text = stringResource(R.string.calendar_status_current),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = null,
                    modifier = Modifier.height(32.dp)
                )
            } else {
                AssistChip(
                    onClick = onSwitchActive,
                    label = {
                        Text(
                            text = stringResource(R.string.action_set_as_current),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.primary
                    ),
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.height(32.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.action_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete_calendar)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_delete),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyVivoCalendarsCard(
    onNewCalendarClick: () -> Unit,
    onPickIcsFile: () -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = Color.Transparent
        ),
        border = BorderStroke(
            1.dp, 
            MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_school),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.empty_calendars_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.empty_calendars_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onPickIcsFile) {
                    Text(stringResource(R.string.action_import_file))
                }
                FilledTonalButton(onClick = onNewCalendarClick) {
                    Text(stringResource(R.string.action_new_calendar))
                }
            }
        }
    }
}

@Composable
private fun PermissionBannerCard(onRequestPermission: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.permission_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.permission_banner_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Button(onClick = onRequestPermission) {
                Text(stringResource(R.string.action_authorize))
            }
        }
    }
}

@Preview(name = "HomeScreen - Preview", showBackground = true)
@Composable
fun HomeScreenPreview() {
    VivoSyncTheme {
        HomeScreenContent(
            uiState = VivoSyncUiState(
                hasCalendarPermission = true,
                vivoCalendars = listOf(
                    VivoCourseCalendar(
                        id = 1L,
                        displayName = "2026春季学期课表",
                        isActive = true,
                        courseCount = 7
                    ),
                    VivoCourseCalendar(
                        id = 2L,
                        displayName = "2025秋季学期课表",
                        isActive = false,
                        courseCount = 8
                    )
                )
            ),
            onPickIcsFile = {},
            onRequestPermission = {},
            onRefreshCalendars = {},
            onOpenSchoolSelector = {},
            onDismissCoursesPreview = {},
            onSyncCoursesToVivoCalendar = { _, _ -> },
            onSetShowNewCalendarDialog = {},
            onCreateNewVivoCalendar = {},
            onSwitchActiveVivoCalendar = {},
            onOpenInVivoCalendar = {},
            onDeleteVivoCalendar = {},
            onClearToastMessage = {}
        )
    }
}

@Preview(name = "HomeScreen - Speed Dial Expanded", showBackground = true)
@Composable
fun HomeScreenSpeedDialExpandedPreview() {
    VivoSyncTheme {
        HomeScreenContent(
            uiState = VivoSyncUiState(
                hasCalendarPermission = true,
                vivoCalendars = listOf(
                    VivoCourseCalendar(
                        id = 1L,
                        displayName = "2026春季学期课表",
                        isActive = true,
                        courseCount = 7
                    )
                )
            ),
            initialFabMenuExpanded = true,
            onPickIcsFile = {},
            onRequestPermission = {},
            onRefreshCalendars = {},
            onOpenSchoolSelector = {},
            onDismissCoursesPreview = {},
            onSyncCoursesToVivoCalendar = { _, _ -> },
            onSetShowNewCalendarDialog = {},
            onCreateNewVivoCalendar = {},
            onSwitchActiveVivoCalendar = {},
            onOpenInVivoCalendar = {},
            onDeleteVivoCalendar = {},
            onClearToastMessage = {}
        )
    }
}

@Preview(name = "HomeScreen - With Snackbar", showBackground = true)
@Composable
fun HomeScreenWithSnackbarPreview() {
    VivoSyncTheme {
        HomeScreenContent(
            uiState = VivoSyncUiState(
                hasCalendarPermission = true,
                toastMessage = "同步成功",
                vivoCalendars = listOf(
                    VivoCourseCalendar(
                        id = 1L,
                        displayName = "大二上",
                        isActive = true,
                        courseCount = 24
                    ),
                    VivoCourseCalendar(
                        id = 2L,
                        displayName = "大一下",
                        isActive = false,
                        courseCount = 26
                    ),
                    VivoCourseCalendar(
                        id = 3L,
                        displayName = "大一上",
                        isActive = false,
                        courseCount = 24
                    )
                )
            ),
            onPickIcsFile = {},
            onRequestPermission = {},
            onRefreshCalendars = {},
            onOpenSchoolSelector = {},
            onDismissCoursesPreview = {},
            onSyncCoursesToVivoCalendar = { _, _ -> },
            onSetShowNewCalendarDialog = {},
            onCreateNewVivoCalendar = {},
            onSwitchActiveVivoCalendar = {},
            onOpenInVivoCalendar = {},
            onDeleteVivoCalendar = {},
            onClearToastMessage = {},
            snackbarHost = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    ExpressiveSnackbarContent(
                        data = object : SnackbarData {
                            override val visuals = object : SnackbarVisuals {
                                override val message: String = "同步成功"
                                override val actionLabel: String? = null
                                override val withDismissAction: Boolean = false
                                override val duration: SnackbarDuration = SnackbarDuration.Short
                            }
                            override fun dismiss() {}
                            override fun performAction() {}
                        }
                    )
                }
            },
            bottomBar = {
                VivoSyncBottomBar(
                    currentDestination = AppDestination.HOME,
                    onDestinationChange = {}
                )
            }
        )
    }
}

@Preview(name = "HomeScreen - With Snackbar Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun HomeScreenWithSnackbarDarkPreview() {
    VivoSyncTheme(darkTheme = true) {
        HomeScreenContent(
            uiState = VivoSyncUiState(
                hasCalendarPermission = true,
                toastMessage = "同步成功",
                vivoCalendars = listOf(
                    VivoCourseCalendar(
                        id = 1L,
                        displayName = "大二上",
                        isActive = true,
                        courseCount = 24
                    ),
                    VivoCourseCalendar(
                        id = 2L,
                        displayName = "大一下",
                        isActive = false,
                        courseCount = 26
                    ),
                    VivoCourseCalendar(
                        id = 3L,
                        displayName = "大一上",
                        isActive = false,
                        courseCount = 24
                    )
                )
            ),
            onPickIcsFile = {},
            onRequestPermission = {},
            onRefreshCalendars = {},
            onOpenSchoolSelector = {},
            onDismissCoursesPreview = {},
            onSyncCoursesToVivoCalendar = { _, _ -> },
            onSetShowNewCalendarDialog = {},
            onCreateNewVivoCalendar = {},
            onSwitchActiveVivoCalendar = {},
            onOpenInVivoCalendar = {},
            onDeleteVivoCalendar = {},
            onClearToastMessage = {},
            snackbarHost = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    ExpressiveSnackbarContent(
                        data = object : SnackbarData {
                            override val visuals = object : SnackbarVisuals {
                                override val message: String = "同步成功"
                                override val actionLabel: String? = null
                                override val withDismissAction: Boolean = false
                                override val duration: SnackbarDuration = SnackbarDuration.Short
                            }
                            override fun dismiss() {}
                            override fun performAction() {}
                        }
                    )
                }
            },
            bottomBar = {
                VivoSyncBottomBar(
                    currentDestination = AppDestination.HOME,
                    onDestinationChange = {}
                )
            }
        )
    }
}
