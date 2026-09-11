package com.app.vivosync.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.valentinilk.shimmer.shimmer
import com.app.vivosync.R
import com.app.vivosync.parser.adapter.AdapterItem
import com.app.vivosync.parser.adapter.AdapterRepository
import com.app.vivosync.parser.adapter.SchoolItem
import com.app.vivosync.ui.theme.VivoSyncTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolSelectionSheet(
    repository: AdapterRepository,
    onDismissRequest: () -> Unit,
    onSelectAdapter: (SchoolItem, AdapterItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        SchoolSelectionContent(
            repository = repository,
            onDismissRequest = onDismissRequest,
            onSelectAdapter = onSelectAdapter
        )
    }
}

@Composable
fun SchoolSelectionContent(
    repository: AdapterRepository,
    onDismissRequest: () -> Unit,
    onSelectAdapter: (SchoolItem, AdapterItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var schools by remember { mutableStateOf<List<SchoolItem>>(emptyList()) }
    var selectedSchool by remember { mutableStateOf<SchoolItem?>(null) }
    var adapters by remember { mutableStateOf<List<AdapterItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }

    BackHandler(enabled = selectedSchool != null) {
        selectedSchool = null
        adapters = emptyList()
        errorMessage = null
    }

    LaunchedEffect(selectedSchool, reloadToken) {
        isLoading = true
        errorMessage = null
        val school = selectedSchool
        if (school == null) {
            repository.getSchools(forceRefresh = reloadToken > 0)
                .onSuccess { schools = it }
                .onFailure { errorMessage = it.message }
        } else {
            repository.getAdapters(school, forceRefresh = reloadToken > 0)
                .onSuccess { adapters = it }
                .onFailure { errorMessage = it.message }
        }
        isLoading = false
    }

    val filteredSchools = remember(schools, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            schools
        } else {
            schools.filter { school ->
                school.name.contains(query, ignoreCase = true) ||
                    school.id.contains(query, ignoreCase = true) ||
                    school.initial.equals(query, ignoreCase = true)
            }
        }
    }

    SchoolSelectionContentLayout(
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        selectedSchool = selectedSchool,
        filteredSchools = filteredSchools,
        adapters = adapters,
        isLoading = isLoading,
        errorMessage = errorMessage,
        onBackToSchools = {
            selectedSchool = null
            adapters = emptyList()
            errorMessage = null
        },
        onSelectSchool = { school ->
            reloadToken = 0
            selectedSchool = school
        },
        onSelectAdapter = onSelectAdapter,
        onRetry = { reloadToken++ },
        modifier = modifier.fillMaxHeight(0.9f)
    )
}

@Composable
fun SchoolSelectionContentLayout(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedSchool: SchoolItem?,
    filteredSchools: List<SchoolItem>,
    adapters: List<AdapterItem>,
    isLoading: Boolean,
    errorMessage: String?,
    onBackToSchools: () -> Unit,
    onSelectSchool: (SchoolItem) -> Unit,
    onSelectAdapter: (SchoolItem, AdapterItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedSchool != null) {
                IconButton(
                    onClick = onBackToSchools,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.action_back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = if (selectedSchool == null) {
                    stringResource(R.string.school_select_title)
                } else {
                    stringResource(R.string.adapter_select_title)
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (selectedSchool == null) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.school_search_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        when {
            isLoading -> {
                SchoolSelectionSkeletonList()
            }

            errorMessage != null -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = onRetry) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }

            selectedSchool == null && filteredSchools.isEmpty() -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.school_not_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            selectedSchool == null -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredSchools, key = { it.id }) { school ->
                        SchoolRow(
                            school = school,
                            onClick = { onSelectSchool(school) }
                        )
                    }
                }
            }

            adapters.isEmpty() -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.adapter_load_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                val school = requireNotNull(selectedSchool)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(adapters, key = { it.adapterId }) { adapter ->
                        AdapterRow(
                            school = school,
                            adapter = adapter,
                            onClick = { onSelectAdapter(school, adapter) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SchoolRow(
    school: SchoolItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_school),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = school.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AdapterRow(
    school: SchoolItem,
    adapter: AdapterItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_extension),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = adapter.adapterName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (adapter.importUrl.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.adapter_direct_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = adapter.description.ifBlank { school.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SchoolSelectionSkeletonList(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shimmer(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(5) { index ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = RoundedCornerShape(14.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .height(20.dp)
                            .fillMaxWidth(if (index % 2 == 0) 0.65f else 0.45f)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = RoundedCornerShape(6.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomSheetPreviewContainer(
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(560.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
            )
            content()
        }
    }
}

private val MOCK_PREVIEW_SCHOOLS = listOf(
    SchoolItem(id = "UPC", name = "中国石油大学(华东)", initial = "Z", resourceFolder = "UPC"),
    SchoolItem(id = "PKU", name = "北京大学", initial = "B", resourceFolder = "PKU"),
    SchoolItem(id = "THU", name = "清华大学", initial = "Q", resourceFolder = "THU"),
    SchoolItem(id = "ZJU", name = "浙江大学", initial = "Z", resourceFolder = "ZJU"),
    SchoolItem(id = "GLOBAL_ZF", name = "通用正方教务系统", initial = "T", resourceFolder = "GLOBAL_ZF")
)

private val MOCK_PREVIEW_ADAPTERS = listOf(
    AdapterItem(
        adapterId = "UPC_JW",
        adapterName = "强智教务系统 (WebVPN)",
        category = "UNDERGRADUATE",
        scriptPath = "upc.js",
        importUrl = "https://webvpn.upc.edu.cn",
        description = "中国石油大学(华东)强智教务适配，登录进入教务系统页面即可点击导入",
        maintainer = "星河欲转"
    ),
    AdapterItem(
        adapterId = "UPC_GRAD",
        adapterName = "研究生综合管理系统",
        category = "POSTGRADUATE",
        scriptPath = "upc_graduate.js",
        importUrl = "https://degrees.upc.edu.cn/",
        description = "中国石油大学(华东)研究生综合管理系统课表导入",
        maintainer = "Haooz"
    )
)

@Preview(name = "BottomSheet - 高校检索", showBackground = true)
@Composable
fun SchoolSelectionSheetSchoolsPreview() {
    VivoSyncTheme {
        BottomSheetPreviewContainer {
            SchoolSelectionContentLayout(
                searchQuery = "",
                onSearchQueryChange = {},
                selectedSchool = null,
                filteredSchools = MOCK_PREVIEW_SCHOOLS,
                adapters = emptyList(),
                isLoading = false,
                errorMessage = null,
                onBackToSchools = {},
                onSelectSchool = {},
                onSelectAdapter = { _, _ -> },
                onRetry = {},
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Preview(name = "BottomSheet - 适配器选择", showBackground = true)
@Composable
fun SchoolSelectionSheetAdaptersPreview() {
    VivoSyncTheme {
        BottomSheetPreviewContainer {
            SchoolSelectionContentLayout(
                searchQuery = "",
                onSearchQueryChange = {},
                selectedSchool = MOCK_PREVIEW_SCHOOLS.first(),
                filteredSchools = emptyList(),
                adapters = MOCK_PREVIEW_ADAPTERS,
                isLoading = false,
                errorMessage = null,
                onBackToSchools = {},
                onSelectSchool = {},
                onSelectAdapter = { _, _ -> },
                onRetry = {},
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Preview(name = "BottomSheet - 深色模式", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun SchoolSelectionSheetDarkPreview() {
    VivoSyncTheme(darkTheme = true) {
        BottomSheetPreviewContainer {
            SchoolSelectionContentLayout(
                searchQuery = "",
                onSearchQueryChange = {},
                selectedSchool = MOCK_PREVIEW_SCHOOLS.first(),
                filteredSchools = emptyList(),
                adapters = MOCK_PREVIEW_ADAPTERS,
                isLoading = false,
                errorMessage = null,
                onBackToSchools = {},
                onSelectSchool = {},
                onSelectAdapter = { _, _ -> },
                onRetry = {},
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Preview(name = "BottomSheet - 加载状态", showBackground = true)
@Composable
fun SchoolSelectionSheetLoadingPreview() {
    VivoSyncTheme {
        BottomSheetPreviewContainer {
            SchoolSelectionContentLayout(
                searchQuery = "",
                onSearchQueryChange = {},
                selectedSchool = null,
                filteredSchools = emptyList(),
                adapters = emptyList(),
                isLoading = true,
                errorMessage = null,
                onBackToSchools = {},
                onSelectSchool = {},
                onSelectAdapter = { _, _ -> },
                onRetry = {},
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
