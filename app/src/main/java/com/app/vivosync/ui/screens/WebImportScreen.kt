package com.app.vivosync.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.app.vivosync.R
import com.app.vivosync.model.CourseItem
import com.app.vivosync.model.TimeSlot
import com.app.vivosync.parser.adapter.AdapterItem
import com.app.vivosync.parser.adapter.AdapterRepository
import com.app.vivosync.parser.adapter.ImportCourseConfigJsonModel
import com.app.vivosync.parser.adapter.ImportCourseJsonModel
import com.app.vivosync.parser.adapter.SchoolItem
import com.app.vivosync.parser.adapter.WebBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 网页教务课表导入提取界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun WebImportScreen(
    school: SchoolItem,
    adapter: AdapterItem,
    repository: AdapterRepository,
    onBack: () -> Unit,
    onCoursesExtracted: (List<CourseItem>, ImportCourseConfigJsonModel?, List<TimeSlot>?) -> Unit,
    onToast: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf(school.name) }
    var currentUrl by remember { mutableStateOf(adapter.importUrl.ifBlank { "about:blank" }) }
    var loadingProgress by remember { mutableFloatStateOf(0f) }
    var isExecutingScript by remember { mutableStateOf(false) }

    // Dialog 交互状态
    var alertDialogData by remember { mutableStateOf<AlertDialogState?>(null) }
    var singleSelectionData by remember { mutableStateOf<SingleSelectionState?>(null) }

    // 缓存异步返回的课程、开学配置与节次作息，避免多 Promise 乱序竞争
    var pendingCourses by remember { mutableStateOf<List<CourseItem>?>(null) }
    var pendingConfig by remember { mutableStateOf<ImportCourseConfigJsonModel?>(null) }
    var pendingTimeSlots by remember { mutableStateOf<List<TimeSlot>?>(null) }
    var finalizeJob by remember { mutableStateOf<Job?>(null) }

    fun finalizeImport() {
        finalizeJob?.cancel()
        val courses = pendingCourses
        if (!courses.isNullOrEmpty()) {
            Log.i("VivoSync_Import", "[WebImportScreen] 提取收尾: 课程数=${courses.size}, 开学配置=$pendingConfig, 作息数=${pendingTimeSlots?.size ?: 0}")
            isExecutingScript = false
            onCoursesExtracted(courses, pendingConfig, pendingTimeSlots)
        }
    }

    // 实例化 WebBridge
    val webBridge = remember {
        WebBridge(
            scope = scope,
            onToast = onToast,
            onShowAlert = { title, content, confirmText, callback ->
                alertDialogData = AlertDialogState(title, content, confirmText, callback)
            },
            onShowSingleSelection = { title, items, defIdx, callback ->
                singleSelectionData = SingleSelectionState(title, items, defIdx, callback)
            },
            onConfigImported = { config ->
                Log.i("VivoSync_Import", "[WebImportScreen] 捕获到开学配置: semesterStartDate=${config.semesterStartDate}, totalWeeks=${config.semesterTotalWeeks}")
                pendingConfig = config
                if (pendingCourses != null) {
                    finalizeJob?.cancel()
                    finalizeJob = scope.launch {
                        delay(400)
                        finalizeImport()
                    }
                }
            },
            onTimeSlotsImported = { slots ->
                Log.i("VivoSync_Import", "[WebImportScreen] 捕获到作息时间配置: ${slots.size} 节")
                pendingTimeSlots = slots
            },
            onCoursesImported = { importedList ->
                val convertedCourses = mapImportedCourses(importedList)
                Log.i("VivoSync_Import", "[WebImportScreen] 捕获到课程数据: ${convertedCourses.size} 门")
                pendingCourses = convertedCourses
                finalizeJob?.cancel()
                finalizeJob = scope.launch {
                    delay(600)
                    finalizeImport()
                }
            },
            onTaskCompleted = {
                Log.i("VivoSync_Import", "[WebImportScreen] 收到 JS notifyTaskCompletion 信号，立即提交")
                finalizeImport()
            }
        )
    }

    BackHandler {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webBridge.detachWebView()
            webViewInstance?.destroy()
        }
    }

    val toastInjectingScript = stringResource(R.string.toast_injecting_script)
    val toastScriptLoadFailed = stringResource(R.string.toast_script_load_failed)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = school.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = pageTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { webViewInstance?.reload() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_refresh),
                            contentDescription = stringResource(R.string.action_refresh)
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.import_bottom_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            isExecutingScript = true
                            scope.launch {
                                onToast(toastInjectingScript)
                                val script = repository.loadScriptContent(school, adapter).getOrElse {
                                    Log.e("VivoSync_Import", "加载远程适配脚本失败", it)
                                    onToast(it.message ?: toastScriptLoadFailed)
                                    isExecutingScript = false
                                    return@launch
                                }

                                val fullCode = """
                                    ${WebBridge.JS_BRIDGE_INIT}
                                    $script
                                """.trimIndent()

                                webViewInstance?.evaluateJavascript(fullCode, null)
                            }
                        },
                        enabled = !isExecutingScript
                    ) {
                        if (isExecutingScript) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isExecutingScript) stringResource(R.string.action_extracting) else stringResource(R.string.action_execute_import))
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (adapter.importUrl.isBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var urlInput by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            placeholder = { Text(stringResource(R.string.url_input_placeholder)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            var u = urlInput.trim()
                            if (!u.startsWith("http://") && !u.startsWith("https://")) {
                                u = "http://$u"
                            }
                            currentUrl = u
                            webViewInstance?.loadUrl(u)
                        }) {
                            Text(stringResource(R.string.action_go))
                        }
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }

                            webBridge.attachWebView(this)
                            addJavascriptInterface(webBridge, WebBridge.BRIDGE_NAME)

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadingProgress = newProgress / 100f
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank() && !title.startsWith("http")) {
                                        pageTitle = title
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    loadingProgress = 0.1f
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loadingProgress = 1.0f
                                    evaluateJavascript(WebBridge.JS_BRIDGE_INIT, null)
                                }
                            }

                            if (currentUrl != "about:blank") {
                                loadUrl(currentUrl)
                            }
                            webViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (loadingProgress in 0.01f..0.99f) {
                LinearProgressIndicator(
                    progress = { loadingProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }
        }
    }

    // JS 弹窗 Alert 支持
    alertDialogData?.let { alert ->
        AlertDialog(
            onDismissRequest = {
                alert.callback(false)
                alertDialogData = null
            },
            title = { Text(alert.title.ifBlank { stringResource(R.string.dialog_hint_title) }) },
            text = { Text(alert.content) },
            confirmButton = {
                TextButton(onClick = {
                    alert.callback(true)
                    alertDialogData = null
                }) {
                    Text(alert.confirmText ?: stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    alert.callback(false)
                    alertDialogData = null
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // JS 单选列表支持
    singleSelectionData?.let { selection ->
        var selectedIdx by remember { mutableIntStateOf(if (selection.defaultIndex >= 0) selection.defaultIndex else 0) }

        AlertDialog(
            onDismissRequest = {
                selection.callback(-1)
                singleSelectionData = null
            },
            title = { Text(selection.title.ifBlank { stringResource(R.string.dialog_select_title) }) },
            text = {
                Column {
                    selection.items.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedIdx == index,
                                onClick = { selectedIdx = index }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = item, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selection.callback(selectedIdx)
                    singleSelectionData = null
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selection.callback(-1)
                    singleSelectionData = null
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private data class AlertDialogState(
    val title: String,
    val content: String,
    val confirmText: String?,
    val callback: (Boolean) -> Unit
)

private data class SingleSelectionState(
    val title: String,
    val items: List<String>,
    val defaultIndex: Int,
    val callback: (Int) -> Unit
)

/**
 * 将提取到的课程格式转换为 VivoSync 课程模型并分配 OriginOS 原子色彩
 */
private fun mapImportedCourses(rawList: List<ImportCourseJsonModel>): List<CourseItem> {
    return rawList.mapIndexed { index, item ->
        val startSec = item.startSection ?: 1
        val endSec = item.endSection ?: startSec
        val colorIdx = (index % 12) + 1

        CourseItem(
            name = item.name.trim(),
            teacher = item.teacher.trim(),
            room = item.position.trim(),
            dayOfWeek = if (item.day in 1..7) item.day else 1,
            startSection = startSec,
            endSection = endSec,
            weeks = item.weeks.ifEmpty { listOf(1) },
            vivoColorIndex = colorIdx
        )
    }
}
