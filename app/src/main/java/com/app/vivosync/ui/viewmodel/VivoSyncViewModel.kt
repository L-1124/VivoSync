package com.app.vivosync.ui.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.vivosync.R
import com.app.vivosync.data.ThemePreferencesRepository
import com.app.vivosync.data.ThemePreferencesRepositoryImpl
import com.app.vivosync.data.CalendarSlotBindingRepository
import com.app.vivosync.data.CalendarSlotBindingRepositoryImpl
import com.app.vivosync.engine.VivoCourseCalendarManager
import com.app.vivosync.engine.VivoNativeProviderSyncClient
import com.app.vivosync.guard.VivoEnvironmentGuard
import com.app.vivosync.model.CourseItem
import com.app.vivosync.model.SyncPayload
import com.app.vivosync.parser.IcsScheduleParser
import com.app.vivosync.parser.adapter.AdapterItem
import com.app.vivosync.parser.adapter.AdapterRepository
import com.app.vivosync.parser.adapter.ImportCourseConfigJsonModel
import com.app.vivosync.parser.adapter.SchoolItem
import com.app.vivosync.permission.PermissionManager
import com.app.vivosync.ui.state.VivoSyncUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VivoSyncViewModel(
    application: Application
) : AndroidViewModel(application) {

    private fun getString(@StringRes resId: Int, vararg args: Any): String {
        return getApplication<Application>().getString(resId, *args)
    }

    private val themePreferencesRepository: ThemePreferencesRepository = ThemePreferencesRepositoryImpl(application.applicationContext)
    private val networkPreferencesRepository: com.app.vivosync.data.NetworkPreferencesRepository = com.app.vivosync.data.NetworkPreferencesRepositoryImpl(application.applicationContext)
    private val slotBindingRepo: CalendarSlotBindingRepository = CalendarSlotBindingRepositoryImpl(application.applicationContext)

    private val _uiState = MutableStateFlow(VivoSyncUiState())
    val uiState: StateFlow<VivoSyncUiState> = _uiState.asStateFlow()

    val calendarManager = VivoCourseCalendarManager(application.applicationContext)
    val adapterRepository = AdapterRepository(application.applicationContext)
    private val environmentGuard = VivoEnvironmentGuard(application.applicationContext)
    private val syncClient = VivoNativeProviderSyncClient(application.applicationContext)
    private val previewExporter = com.app.vivosync.engine.VivoCoursePreviewExporter(application.applicationContext)

    init {
        viewModelScope.launch {
            themePreferencesRepository.themePreferencesFlow.collect { preferences ->
                _uiState.update {
                    it.copy(
                        followSystemDynamicColor = preferences.followSystemDynamicColor,
                        followSystemDarkTheme = preferences.followSystemDarkTheme
                    )
                }
            }
        }
        viewModelScope.launch {
            networkPreferencesRepository.selectedMirrorNodeFlow.collect { node ->
                adapterRepository.selectedMirrorNode = node
                _uiState.update {
                    it.copy(selectedMirrorNode = node)
                }
            }
        }
        refreshStatus()
    }

    fun refreshStatus() {
        viewModelScope.launch {
            val env = withContext(Dispatchers.IO) { environmentGuard.checkCompatibility() }
            val hasPerm = PermissionManager.hasCalendarPermissions(getApplication())
            _uiState.update {
                it.copy(
                    envStatus = env,
                    hasCalendarPermission = hasPerm
                )
            }
            if (hasPerm) {
                refreshCalendars()
            } else {
                _uiState.update {
                    it.copy(
                        vivoCalendars = emptyList(),
                        targetCalendarId = null
                    )
                }
            }
        }
    }

    fun refreshCalendars() {
        if (!PermissionManager.hasCalendarPermissions(getApplication())) {
            _uiState.update {
                it.copy(
                    vivoCalendars = emptyList(),
                    targetCalendarId = null
                )
            }
            return
        }

        viewModelScope.launch {
            val calendars = calendarManager.getVivoCourseCalendars()
            val exportResult = slotBindingRepo.checkPendingExportResult(calendars)
            val active = calendars.firstOrNull { it.isActive }

            when (exportResult) {
                is com.app.vivosync.data.PendingExportResult.Success -> {
                    val newCal = exportResult.calendar
                    _uiState.update {
                        it.copy(
                            vivoCalendars = calendars,
                            targetCalendarId = newCal.id,
                            statusMessage = "课程表「${newCal.displayName}」导入并保存成功！作息已同步绑定",
                            toastMessage = getString(R.string.toast_import_save_success, newCal.displayName)
                        )
                    }
                }
                is com.app.vivosync.data.PendingExportResult.Cancelled -> {
                    _uiState.update {
                        it.copy(
                            vivoCalendars = calendars,
                            targetCalendarId = it.targetCalendarId ?: active?.id ?: calendars.firstOrNull()?.id,
                            statusMessage = "未检测到新课程表保存，已取消导入",
                            toastMessage = getString(R.string.toast_import_cancelled)
                        )
                    }
                }
                is com.app.vivosync.data.PendingExportResult.NoPending -> {
                    _uiState.update {
                        it.copy(
                            vivoCalendars = calendars,
                            targetCalendarId = it.targetCalendarId ?: active?.id ?: calendars.firstOrNull()?.id
                        )
                    }
                }
            }
        }
    }

    fun createNewVivoCalendar(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOperatingCalendar = true, showNewCalendarDialog = false) }
            runCatching {
                val newId = calendarManager.createVivoCourseCalendar(name, activate = true)
                refreshCalendars()
                _uiState.update {
                    it.copy(
                        targetCalendarId = newId,
                        statusMessage = "已新建 vivo 课程表「$name」",
                        toastMessage = getString(R.string.toast_calendar_created, name)
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        statusMessage = "新建课表失败: ${e.message}",
                        toastMessage = getString(R.string.toast_calendar_create_failed, e.message ?: "")
                    )
                }
            }
            _uiState.update { it.copy(isOperatingCalendar = false) }
        }
    }

    fun switchActiveVivoCalendar(calendarId: Long, autoOpenCalendar: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOperatingCalendar = true) }
            val ok = calendarManager.switchActiveCourseCalendar(calendarId, autoOpenCalendar = autoOpenCalendar)
            refreshCalendars()
            _uiState.update {
                it.copy(
                    isOperatingCalendar = false,
                    targetCalendarId = calendarId,
                    statusMessage = if (ok) "已切换激活课表并联动 vivo 日历" else "切换激活课表失败",
                    toastMessage = if (ok) getString(R.string.toast_calendar_switched) else getString(R.string.toast_calendar_switch_failed)
                )
            }
        }
    }

    fun openInVivoCalendar(calendarId: Long) {
        val ok = calendarManager.openInVivoCalendar(calendarId)
        if (!ok) {
            showToast(getString(R.string.toast_calendar_not_found))
        }
    }

    fun deleteVivoCalendar(calendarId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOperatingCalendar = true) }
            val ok = calendarManager.deleteVivoCourseCalendar(calendarId)
            if (ok) {
                slotBindingRepo.unbind(calendarId)
            }
            refreshCalendars()
            _uiState.update {
                it.copy(
                    isOperatingCalendar = false,
                    statusMessage = if (ok) "课表已从系统日历删除" else "删除失败",
                    toastMessage = if (ok) getString(R.string.toast_calendar_deleted) else getString(R.string.toast_calendar_delete_failed)
                )
            }
        }
    }

    fun selectSchoolAdapter(school: SchoolItem, adapter: AdapterItem) {
        _uiState.update {
            it.copy(
                activeSchool = school,
                activeAdapter = adapter
            )
        }
    }

    fun onCoursesExtracted(
        courses: List<CourseItem>,
        config: ImportCourseConfigJsonModel? = null,
        timeSlots: List<com.app.vivosync.model.TimeSlot>? = null
    ) {
        val parsedDate = com.app.vivosync.engine.VivoDateParser.parseSemesterStartDate(config?.semesterStartDate)
        android.util.Log.i("VivoSync_Import", "======== 课表提取完成 ========")
        android.util.Log.i("VivoSync_Import", "提取到课程数量: ${courses.size}")
        android.util.Log.i("VivoSync_Import", "提取到原始开学配置: $config")
        android.util.Log.i("VivoSync_Import", "解析后的开学日期: $parsedDate (周${parsedDate?.dayOfWeek?.value})")
        android.util.Log.i("VivoSync_Import", "提取到节次作息配置: ${timeSlots?.size ?: 0} 节")

        _uiState.update {
            it.copy(
                importedCoursesPreview = courses,
                importedConfig = config,
                importedStartDate = parsedDate,
                importedTimeSlots = timeSlots
            )
        }
    }

    fun dismissCoursesPreview() {
        _uiState.update {
            it.copy(
                importedCoursesPreview = null,
                importedConfig = null,
                importedStartDate = null,
                importedTimeSlots = null
            )
        }
    }

    fun setShowNewCalendarDialog(show: Boolean) {
        _uiState.update { it.copy(showNewCalendarDialog = show) }
    }

    fun generateDefaultScheduleName(startDate: java.time.LocalDate?): String {
        val date = startDate ?: java.time.LocalDate.now()
        val year = date.year
        val semester = if ((date.monthValue in 8..12) || (date.monthValue == 1)) "秋季学期" else "春季学期"
        return "${year}年$semester"
    }

    fun syncCoursesToVivoCalendar(calendarId: Long?, courses: List<CourseItem>, newCalendarName: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            try {
                val payload = buildSyncPayload(courses)
                if (calendarId == null || calendarId <= 0) {
                    syncNewCalendar(payload, newCalendarName)
                } else {
                    syncExistingCalendar(calendarId, payload)
                }
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    private fun buildSyncPayload(courses: List<CourseItem>): SyncPayload {
        val state = _uiState.value
        return SyncPayload(
            semesterStartDate = state.importedStartDate ?: java.time.LocalDate.now(),
            semesterTotalWeeks = state.importedConfig?.semesterTotalWeeks ?: 20,
            courses = courses,
            timeSlots = state.importedTimeSlots.orEmpty()
        )
    }

    private suspend fun syncNewCalendar(payload: SyncPayload, requestedName: String) {
        val finalName = requestedName.trim().ifBlank { generateDefaultScheduleName(payload.semesterStartDate) }
        android.util.Log.i("VivoSync_Sync", "新建课表模式 -> 唤起 VivoCoursePreviewExporter, 课表名称=$finalName")
        if (payload.timeSlots.isNotEmpty()) {
            val fingerprint = slotBindingRepo.calculateFingerprint(payload.timeSlots)
            val existingIds = calendarManager.getVivoCourseCalendars().asSequence().map { it.id }.toSet()
            slotBindingRepo.setPendingExport(existingIds, finalName, fingerprint)
        }

        previewExporter.exportToVivoPreview(payload, scheduleName = finalName)
            .onSuccess {
                clearImportedPreview(
                    statusMessage = "已唤起 vivo 官方课程表导入界面（将以「$finalName」创建）",
                    toastMessage = getString(R.string.toast_system_import_invoked)
                )
            }
            .onFailure { e ->
                _uiState.update {
                    it.copy(
                        statusMessage = "唤起 vivo 导入界面失败: ${e.message}",
                        toastMessage = getString(R.string.toast_invoke_failed, e.message ?: "")
                    )
                }
            }
    }

    private suspend fun syncExistingCalendar(calendarId: Long, payload: SyncPayload) {
        val calendarName = withContext(Dispatchers.IO) {
            syncClient.queryCalendarAccountInfo(calendarId)?.displayName.orEmpty()
        }
        android.util.Log.i("VivoSync_Sync", "======== [用户选择已有课表] calendarId=#$calendarId ('$calendarName'), 待同步课程数=${payload.courses.size}, 节次配置数=${payload.timeSlots.size} ========")

        val isSlotMatching = slotBindingRepo.isSlotMatching(calendarId, payload.timeSlots)
        val hasEvents = withContext(Dispatchers.IO) { syncClient.queryTotalEventsCount(calendarId) > 0 }
        android.util.Log.i("VivoSync_Sync", "作息比对判断结果: isSlotMatching=$isSlotMatching, hasEvents=$hasEvents -> canSilentSync=${isSlotMatching && hasEvents}")

        if (isSlotMatching && hasEvents) {
            syncExistingCalendarSilently(calendarId, payload)
        } else {
            reinitializeExistingCalendar(calendarId, calendarName, payload)
        }
    }

    private suspend fun syncExistingCalendarSilently(calendarId: Long, payload: SyncPayload) {
        android.util.Log.i("VivoSync_Sync", "已有课表 #$calendarId 作息指纹完全一致且非空 -> 进入静默同步")
        syncClient.syncCoursesSilent(calendarId, payload)
            .onSuccess { count ->
                android.util.Log.i("VivoSync_Sync", "静默同步成功! 已写入 $count 门日程")
                clearImportedPreview(
                    statusMessage = "作息一致，同步成功！已更新 vivo 课程表（$count 门日程）",
                    toastMessage = getString(R.string.toast_sync_success_silent)
                )
                refreshCalendars()
            }
            .onFailure { e ->
                android.util.Log.e("VivoSync_Sync", "静默同步失败: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        statusMessage = "同步失败: ${e.message}",
                        toastMessage = getString(R.string.toast_sync_failed, e.message ?: "")
                    )
                }
            }
    }

    private suspend fun reinitializeExistingCalendar(calendarId: Long, calendarName: String, payload: SyncPayload) {
        android.util.Log.i("VivoSync_Sync", "已有课表 #$calendarId 作息未绑定或发生变更，准备执行【安全删除旧日历 + 原名官方预览重新导入】")
        withContext(Dispatchers.IO) {
            syncClient.deleteCalendar(calendarId)
            slotBindingRepo.unbind(calendarId)
            val existingIds = calendarManager.getVivoCourseCalendars().asSequence().map { it.id }.toSet()
            if (calendarName.isNotBlank() && payload.timeSlots.isNotEmpty()) {
                val fingerprint = slotBindingRepo.calculateFingerprint(payload.timeSlots)
                slotBindingRepo.setPendingExport(existingIds, calendarName, fingerprint)
            }
        }

        previewExporter.exportToVivoPreview(payload, scheduleName = calendarName)
            .onSuccess {
                android.util.Log.i("VivoSync_Sync", "已成功删除旧日历并带着名称 '$calendarName' 唤起 vivo 官方预览界面")
                clearImportedPreview(
                    statusMessage = "检测到作息未初始化或发生变化，已重置旧课表并唤起系统日历为您同步",
                    toastMessage = getString(R.string.toast_slots_diff_invoking)
                )
            }
            .onFailure { e ->
                android.util.Log.e("VivoSync_Sync", "唤起系统日历失败: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        statusMessage = "唤起系统日历失败: ${e.message}",
                        toastMessage = getString(R.string.toast_invoke_failed, e.message ?: "")
                    )
                }
            }
    }

    private fun clearImportedPreview(statusMessage: String, toastMessage: String) {
        _uiState.update {
            it.copy(
                importedCoursesPreview = null,
                importedConfig = null,
                importedStartDate = null,
                importedTimeSlots = null,
                statusMessage = statusMessage,
                toastMessage = toastMessage
            )
        }
    }

    fun importIcsContent(content: String) {
        viewModelScope.launch {
            runCatching {
                val parsed = IcsScheduleParser.parse(content)
                if (parsed.courses.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            importedCoursesPreview = parsed.courses,
                            statusMessage = "成功读取 ICS（${parsed.courses.size} 门课程）"
                        )
                    }
                } else {
                    _uiState.update { it.copy(statusMessage = "ICS 文件未包含有效课程") }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(statusMessage = "ICS 读取失败: ${e.message}") }
            }
        }
    }

    fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun onPermissionGranted() {
        _uiState.update {
            it.copy(
                hasCalendarPermission = true,
                showRationaleDialog = false,
                showGoToSettingsDialog = false
            )
        }
        refreshCalendars()
    }

    fun onPermissionDenied(shouldShowRationale: Boolean) {
        _uiState.update {
            if (shouldShowRationale) {
                it.copy(showRationaleDialog = true)
            } else {
                it.copy(showGoToSettingsDialog = true)
            }
        }
    }

    fun dismissRationaleDialog() {
        _uiState.update { it.copy(showRationaleDialog = false) }
    }

    fun dismissGoToSettingsDialog() {
        _uiState.update { it.copy(showGoToSettingsDialog = false) }
    }

    fun toggleDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            themePreferencesRepository.setFollowSystemDynamicColor(enabled)
        }
    }

    fun toggleDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            themePreferencesRepository.setFollowSystemDarkTheme(enabled)
        }
    }

    fun selectMirrorNode(node: com.app.vivosync.parser.adapter.MirrorNode) {
        viewModelScope.launch {
            networkPreferencesRepository.setSelectedMirrorNode(node)
        }
    }
}
