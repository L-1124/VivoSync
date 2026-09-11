package com.app.vivosync.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.app.vivosync.R
import com.app.vivosync.parser.adapter.MirrorNode
import com.app.vivosync.ui.component.SegmentedColumn
import com.app.vivosync.ui.component.SegmentedDropdownItem
import com.app.vivosync.ui.component.SegmentedSwitchItem
import com.app.vivosync.ui.theme.VivoSyncTheme

/**
 * VivoSync 通用设置界面
 *
 * 采用 Material 3 Expressive 规范，将设置项目归类为具备自适应内外圆角、
 * 物理弹簧形变动效与触感震动反馈（HapticFeedback）的分段分组卡片（[SegmentedColumn]）。
 * 页面背景底色采用 surfaceContainer，分段卡片采用 surfaceBright，形成具有层次感的浮岛效果。
 *
 * @param followSystemDynamicColor 是否开启 Android 12+ 动态色彩提取
 * @param followSystemDarkTheme 是否跟随系统设置自动切换深色/浅色外观
 * @param selectedMirrorNode 当前选中的教务适配脚本下载镜像源线路
 * @param onDynamicColorChange 动态色彩切换回调
 * @param onDarkThemeChange 深色模式切换回调
 * @param onMirrorNodeChange 教务镜像源切换回调
 * @param modifier 外部修饰符
 * @param bottomBar 底部导航栏插槽
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    followSystemDynamicColor: Boolean,
    followSystemDarkTheme: Boolean,
    selectedMirrorNode: MirrorNode = MirrorNode.AUTO,
    onDynamicColorChange: (Boolean) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onMirrorNodeChange: (MirrorNode) -> Unit = {},
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = bottomBar
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 分组 1: 外观与个性化
            SegmentedColumn(
                title = stringResource(R.string.setting_group_appearance)
            ) {
                item(key = "dynamic_color") {
                    SegmentedSwitchItem(
                        iconResId = R.drawable.ic_palette,
                        title = stringResource(R.string.setting_dynamic_color_title),
                        summary = stringResource(R.string.setting_dynamic_color_desc),
                        checked = followSystemDynamicColor,
                        onCheckedChange = onDynamicColorChange
                    )
                }

                item(key = "dark_theme") {
                    SegmentedSwitchItem(
                        iconResId = R.drawable.ic_dark_mode,
                        title = stringResource(R.string.setting_dark_theme_title),
                        summary = stringResource(R.string.setting_dark_theme_desc),
                        checked = followSystemDarkTheme,
                        onCheckedChange = onDarkThemeChange
                    )
                }
            }

            // 分组 2: 教务网络与加速
            val mirrorNodes = remember { MirrorNode.entries }
            val selectedIndex = mirrorNodes.indexOf(selectedMirrorNode).coerceAtLeast(0)

            SegmentedColumn(
                title = stringResource(R.string.setting_group_network)
            ) {
                item(key = "mirror_node") {
                    SegmentedDropdownItem(
                        iconResId = R.drawable.ic_public,
                        title = stringResource(R.string.setting_mirror_node_title),
                        summary = stringResource(R.string.setting_mirror_node_desc),
                        items = mirrorNodes,
                        selectedIndex = selectedIndex,
                        onItemSelected = { index ->
                            if (index in mirrorNodes.indices) {
                                onMirrorNodeChange(mirrorNodes[index])
                            }
                        },
                        itemLabel = { stringResource(it.titleRes) },
                        itemSummary = { stringResource(it.descRes) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 设置界面 - 浅色模式预览
 */
@Preview(name = "Settings - Light Preview", showBackground = true)
@Composable
fun SettingsScreenLightPreview() {
    VivoSyncTheme {
        SettingsScreen(
            followSystemDynamicColor = true,
            followSystemDarkTheme = false,
            selectedMirrorNode = MirrorNode.AUTO,
            onDynamicColorChange = {},
            onDarkThemeChange = {},
            onMirrorNodeChange = {}
        )
    }
}

/**
 * 设置界面 - 深色模式预览
 */
@Preview(name = "Settings - Dark Preview", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun SettingsScreenDarkPreview() {
    VivoSyncTheme(darkTheme = true) {
        SettingsScreen(
            followSystemDynamicColor = true,
            followSystemDarkTheme = true,
            selectedMirrorNode = MirrorNode.AUTO,
            onDynamicColorChange = {},
            onDarkThemeChange = {},
            onMirrorNodeChange = {}
        )
    }
}
