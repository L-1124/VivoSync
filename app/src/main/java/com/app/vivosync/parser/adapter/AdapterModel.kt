package com.app.vivosync.parser.adapter

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class RootIndexDocument(
    val schools: List<SchoolItem> = emptyList()
)

@Serializable
data class AdapterIndexDocument(
    val adapters: List<AdapterItem> = emptyList()
)

@Serializable
data class CacheMetadata(
    val currentSha: String? = null,
    val previousSha: String? = null,
    val lastCheckedAt: Long = 0L
)

@Serializable
data class SchoolItem(
    val id: String,
    val name: String,
    val initial: String,
    @SerialName("resource_folder")
    val resourceFolder: String
)

@Serializable
data class AdapterItem(
    @SerialName("adapter_id")
    val adapterId: String,
    @SerialName("adapter_name")
    val adapterName: String,
    val category: String,
    @SerialName("asset_js_path")
    val scriptPath: String,
    @SerialName("import_url")
    val importUrl: String = "",
    val description: String = "",
    val maintainer: String = ""
)

/**
 * 开放教务课表规范输出的课程原始 JSON 数据模型（参考 TimeCat / 开放课表规则库输出格式）
 */
@Serializable
data class ImportCourseJsonModel(
    val id: String? = null,
    val name: String = "",
    val teacher: String = "",
    val position: String = "",
    val day: Int = 1,
    val startSection: Int? = null,
    val endSection: Int? = null,
    val weeks: List<Int> = emptyList(),
    val isCustomTime: Boolean = false,
    val customStartTime: String? = null,
    val customEndTime: String? = null,
    val color: Int? = null,
    val remark: String? = null
)

/**
 * 底层 WebBridge 发送给 Native 的统一消息载体
 */
@Serializable
data class BridgeActionMessage(
    val action: String,
    val callbackId: String? = null,
    val payload: String? = null
)

@Serializable
data class ShowAlertPayload(
    val titleText: String = "",
    val contentText: String = "",
    val confirmText: String? = null
)

@Serializable
data class ShowPromptPayload(
    val titleText: String = "",
    val tipText: String = "",
    val defaultText: String = "",
    val validatorJsFunction: String = ""
)

@Serializable
data class ShowSingleSelectionPayload(
    val titleText: String = "",
    val itemsJsonString: String = "[]",
    val defaultSelectedIndex: Int = -1
)

@Serializable
data class SaveCoursesPayload(
    val coursesJsonString: String = "[]"
)

@Serializable
data class ShowToastPayload(
    val message: String = ""
)

@Serializable
data class SaveCourseConfigPayload(
    val configJsonString: String = "{}"
)

@Serializable
data class SaveTimeSlotsPayload(
    val timeSlotsJsonString: String = "[]"
)

@Serializable
data class ImportCourseConfigJsonModel(
    val semesterStartDate: String? = null,
    val semesterTotalWeeks: Int? = null,
    val firstDayOfWeek: Int? = null,
    val defaultClassDuration: Int? = null,
    val defaultBreakDuration: Int? = null
)
