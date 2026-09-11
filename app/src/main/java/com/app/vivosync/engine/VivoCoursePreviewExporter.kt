package com.app.vivosync.engine

import android.content.Context
import android.content.Intent
import com.app.vivosync.guard.VivoEnvironmentGuard
import com.app.vivosync.model.SyncPayload
import com.app.vivosync.model.VivoCourseClassDto
import com.app.vivosync.model.VivoCourseProtocolDto
import com.app.vivosync.model.VivoTimeSettingDto
import com.app.vivosync.model.VivoTimeSlotDto
import kotlinx.serialization.json.Json

/**
 * 方案 1 实现：通过 vivo 系统导出的 CoursePreviewActivity 协议，注入全局作息与完整课表
 */
class VivoCoursePreviewExporter(private val context: Context) {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * 将同步数据载荷组装为符合 vivo 官方规范的 JSON，并唤起系统课程表导入预览界面
     * @param payload 课程与作息载荷
     * @param scheduleName 新建课表名称（若指定则透传给 vivo 官方预览页面）
     */
    fun exportToVivoPreview(payload: SyncPayload, scheduleName: String = ""): Result<Unit> = runCatching {
        // 1. 组装全局作息 timeSetting
        val duration = if (payload.timeSlots.isNotEmpty()) {
            val first = payload.timeSlots.first()
            val diff = first.getEndMinuteOfDay() - first.getStartMinuteOfDay()
            if (diff in 5..180) diff else 45
        } else 45

        val times = payload.timeSlots.map { slot ->
            VivoTimeSlotDto(
                timeSlot = slot.getTimeSlotType(),
                selection = slot.number,
                beginTime = slot.getStartMinuteOfDay(),
                endTime = slot.getEndMinuteOfDay()
            )
        }

        val timeSetting = VivoTimeSettingDto(
            duration = duration,
            breaks = 10,
            times = times
        )

        // 2. 组装课程列表 classes
        val classes = payload.courses.map { course ->
            VivoCourseClassDto(
                className = course.name,
                teacher = course.teacher,
                room = course.room,
                dayOfWeek = course.dayOfWeek,
                start = course.startSection,
                end = course.endSection,
                weeks = course.weeks,
                singleOrBiWeekly = 0
            )
        }

        val nameParam = scheduleName.trim().ifBlank { null }
        val protocol = VivoCourseProtocolDto(
            result = 0,
            message = "success",
            courseName = nameParam,
            tableName = nameParam,
            timeSetting = timeSetting,
            classes = classes
        )

        val jsonString = json.encodeToString(protocol)

        // 3. 构建并启动 Intent
        val intent = Intent(VivoEnvironmentGuard.COURSE_PREVIEW_ACTION).apply {
            setPackage(VivoEnvironmentGuard.VIVO_CALENDAR_PACKAGE)
            putExtra("course_data", jsonString)
            if (nameParam != null) {
                putExtra("course_name", nameParam)
                putExtra("table_name", nameParam)
                putExtra("title", nameParam)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }
}
