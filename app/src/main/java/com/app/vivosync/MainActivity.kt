package com.app.vivosync

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.NavDisplay
import com.app.vivosync.permission.PermissionManager
import com.app.vivosync.ui.navigation.AppDestination
import com.app.vivosync.ui.navigation.AppRoute
import com.app.vivosync.ui.navigation.BottomSheetSceneStrategy
import com.app.vivosync.ui.navigation.TopLevelBackStack
import com.app.vivosync.ui.screens.HomeScreen
import com.app.vivosync.ui.screens.SchoolSelectionContent
import com.app.vivosync.ui.screens.SettingsScreen
import com.app.vivosync.ui.screens.WebImportScreen
import com.app.vivosync.ui.state.VivoSyncUiState
import com.app.vivosync.ui.theme.VivoSyncTheme
import com.app.vivosync.ui.viewmodel.VivoSyncViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: VivoSyncViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val isSystemDark = isSystemInDarkTheme()
            val useDarkTheme = if (uiState.followSystemDarkTheme) isSystemDark else false

            DisposableEffect(useDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (useDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (useDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
                onDispose {}
            }

            VivoSyncTheme(
                darkTheme = useDarkTheme,
                dynamicColor = uiState.followSystemDynamicColor
            ) {
                VivoSyncApp(viewModel = viewModel, uiState = uiState)
            }
        }
    }
}

@Composable
fun VivoSyncApp(
    viewModel: VivoSyncViewModel = viewModel(),
    uiState: VivoSyncUiState = viewModel.uiState.collectAsStateWithLifecycle().value
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.isNotEmpty() && permissions.values.all { it }
        val activity = context as? Activity
        val shouldShowRationale = (activity != null) && PermissionManager.shouldShowRationale(activity)
        if (granted) {
            viewModel.onPermissionGranted()
        } else {
            viewModel.onPermissionDenied(shouldShowRationale)
        }
    }

    fun requestCalendarPermissionFlow() {
        val activity = context as? Activity
        if (activity != null && PermissionManager.shouldShowRationale(activity)) {
            viewModel.onPermissionDenied(shouldShowRationale = true)
        } else {
            permissionLauncher.launch(PermissionManager.CALENDAR_PERMISSIONS)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                } ?: ""
                if (content.isNotBlank()) {
                    viewModel.importIcsContent(content)
                }
            }
        }
    }

    VivoSyncAppContent(
        viewModel = viewModel,
        uiState = uiState,
        onPickIcsFile = { filePickerLauncher.launch("*/*") },
        onRequestPermission = ::requestCalendarPermissionFlow,
        onDynamicColorChange = viewModel::toggleDynamicColor,
        onDarkThemeChange = viewModel::toggleDarkTheme,
        onMirrorNodeChange = viewModel::selectMirrorNode
    )

    if (uiState.showRationaleDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRationaleDialog,
            title = { Text(stringResource(R.string.permission_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(stringResource(R.string.permission_dialog_desc))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissRationaleDialog()
                    permissionLauncher.launch(PermissionManager.CALENDAR_PERMISSIONS)
                }) {
                    Text(stringResource(R.string.permission_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRationaleDialog) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (uiState.showGoToSettingsDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissGoToSettingsDialog,
            title = { Text(stringResource(R.string.permission_disabled_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(stringResource(R.string.permission_disabled_desc))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissGoToSettingsDialog()
                    PermissionManager.openAppSettings(context)
                }) {
                    Text(stringResource(R.string.action_goto_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissGoToSettingsDialog) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VivoSyncAppContent(
    viewModel: VivoSyncViewModel,
    uiState: VivoSyncUiState,
    onPickIcsFile: () -> Unit,
    onRequestPermission: () -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onMirrorNodeChange: (com.app.vivosync.parser.adapter.MirrorNode) -> Unit,
    modifier: Modifier = Modifier,
    topLevelBackStack: TopLevelBackStack<AppRoute> = remember { TopLevelBackStack(AppRoute.Home) }
) {
    val bottomSheetStrategy = remember { BottomSheetSceneStrategy<AppRoute>() }

    val bottomBar: @Composable () -> Unit = {
        VivoSyncBottomBar(
            currentDestination = AppDestination.fromRoute(topLevelBackStack.topLevelKey) ?: AppDestination.HOME,
            onDestinationChange = { destination ->
                topLevelBackStack.addTopLevel(destination.route)
            }
        )
    }

    NavDisplay(
        backStack = topLevelBackStack.backStack,
        onBack = { topLevelBackStack.removeLast() },
        sceneStrategies = listOf(bottomSheetStrategy),
        entryProvider = entryProvider {
            entry<AppRoute.Home>(
                metadata = metadata {
                    put(NavDisplay.TransitionKey) {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                    put(NavDisplay.PopTransitionKey) {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                }
            ) {
                HomeScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onPickIcsFile = onPickIcsFile,
                    onRequestPermission = onRequestPermission,
                    onOpenSchoolSelector = {
                        topLevelBackStack.add(AppRoute.SchoolSelector)
                    },
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = bottomBar
                )
            }
            entry<AppRoute.Settings>(
                metadata = metadata {
                    put(NavDisplay.TransitionKey) {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                    put(NavDisplay.PopTransitionKey) {
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                }
            ) {
                SettingsScreen(
                    followSystemDynamicColor = uiState.followSystemDynamicColor,
                    followSystemDarkTheme = uiState.followSystemDarkTheme,
                    selectedMirrorNode = uiState.selectedMirrorNode,
                    onDynamicColorChange = onDynamicColorChange,
                    onDarkThemeChange = onDarkThemeChange,
                    onMirrorNodeChange = onMirrorNodeChange,
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = bottomBar
                )
            }
            entry<AppRoute.SchoolSelector>(
                metadata = BottomSheetSceneStrategy.bottomSheet()
            ) {
                SchoolSelectionContent(
                    repository = viewModel.adapterRepository,
                    onDismissRequest = { topLevelBackStack.removeLast() },
                    onSelectAdapter = { school, adapter ->
                        viewModel.selectSchoolAdapter(school, adapter)
                        topLevelBackStack.removeLast()
                        topLevelBackStack.add(AppRoute.WebImport)
                    }
                )
            }
            entry<AppRoute.WebImport>(
                metadata = metadata {
                    put(NavDisplay.TransitionKey) {
                        slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(350)
                        ) togetherWith ExitTransition.KeepUntilTransitionsFinished
                    }
                    put(NavDisplay.PopTransitionKey) {
                        EnterTransition.None togetherWith slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(300)
                        )
                    }
                    put(NavDisplay.PredictivePopTransitionKey) {
                        EnterTransition.None togetherWith slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(300)
                        )
                    }
                }
            ) {
                val school = uiState.activeSchool
                val adapter = uiState.activeAdapter
                if (school != null && adapter != null) {
                    WebImportScreen(
                        school = school,
                        adapter = adapter,
                        repository = viewModel.adapterRepository,
                        onBack = {
                            topLevelBackStack.removeLast()
                        },
                        onCoursesExtracted = { courses, config, timeSlots ->
                            viewModel.onCoursesExtracted(courses, config, timeSlots)
                            topLevelBackStack.removeLast()
                        },
                        onToast = { msg -> viewModel.showToast(msg) }
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

@Composable
fun VivoSyncBottomBar(
    currentDestination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        AppDestination.entries.forEach { destination ->
            val isSelected = currentDestination == destination
            NavigationBarItem(
                selected = isSelected,
                onClick = { onDestinationChange(destination) },
                icon = {
                    Icon(
                        painter = painterResource(
                            id = if (isSelected) destination.selectedIconResId else destination.unselectedIconResId
                        ),
                        contentDescription = stringResource(id = destination.contentDescriptionResId),
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = { Text(stringResource(id = destination.labelResId)) }
            )
        }
    }
}

@Preview(name = "BottomBar - 课表同步", showBackground = true)
@Composable
fun BottomBarHomePreview() {
    VivoSyncTheme {
        VivoSyncBottomBar(
            currentDestination = AppDestination.HOME,
            onDestinationChange = {}
        )
    }
}

@Preview(name = "BottomBar - 设置", showBackground = true)
@Composable
fun BottomBarSettingsPreview() {
    VivoSyncTheme {
        VivoSyncBottomBar(
            currentDestination = AppDestination.SETTINGS,
            onDestinationChange = {}
        )
    }
}

@Preview(name = "Full Screen - 设置页与底部导航栏", showBackground = true)
@Composable
fun MainSettingsFullScreenPreview() {
    VivoSyncTheme {
        SettingsScreen(
            followSystemDynamicColor = true,
            followSystemDarkTheme = false,
            selectedMirrorNode = com.app.vivosync.parser.adapter.MirrorNode.AUTO,
            onDynamicColorChange = {},
            onDarkThemeChange = {},
            onMirrorNodeChange = {},
            bottomBar = {
                VivoSyncBottomBar(
                    currentDestination = AppDestination.SETTINGS,
                    onDestinationChange = {}
                )
            }
        )
    }
}
