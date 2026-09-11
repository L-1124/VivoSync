package com.app.vivosync.ui.state

import com.app.vivosync.guard.VivoSupportStatus
import com.app.vivosync.model.CourseItem
import com.app.vivosync.model.VivoCourseCalendar
import com.app.vivosync.parser.adapter.AdapterItem
import com.app.vivosync.parser.adapter.ImportCourseConfigJsonModel
import com.app.vivosync.parser.adapter.SchoolItem

data class VivoSyncUiState(
    val envStatus: VivoSupportStatus = VivoSupportStatus.Supported,
    val hasCalendarPermission: Boolean = false,
    val showRationaleDialog: Boolean = false,
    val showGoToSettingsDialog: Boolean = false,
    val followSystemDynamicColor: Boolean = true,
    val followSystemDarkTheme: Boolean = true,
    val selectedMirrorNode: com.app.vivosync.parser.adapter.MirrorNode = com.app.vivosync.parser.adapter.MirrorNode.AUTO,

    // vivo 原生课程表管理状态
    val vivoCalendars: List<VivoCourseCalendar> = emptyList(),
    val targetCalendarId: Long? = null,
    val isOperatingCalendar: Boolean = false,
    val showNewCalendarDialog: Boolean = false,

    // 网页教务课表导入体系状态
    val activeSchool: SchoolItem? = null,
    val activeAdapter: AdapterItem? = null,
    val importedCoursesPreview: List<CourseItem>? = null,
    val importedConfig: ImportCourseConfigJsonModel? = null,
    val importedStartDate: java.time.LocalDate? = null,
    val importedTimeSlots: List<com.app.vivosync.model.TimeSlot>? = null,

    // 同步中与通知提示
    val isSyncing: Boolean = false,
    val statusMessage: String = "就绪",
    val toastMessage: String? = null
)
