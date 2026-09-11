package com.app.vivosync.engine

import com.app.vivosync.model.CourseItem
import com.app.vivosync.model.TimeSlot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 日期对齐与时间戳计算引擎
 */
object VivoDateAligner {

    private const val TAG = "VivoSync_Date"

    /**
     * 计算学期基准第 1 周周一的绝对日期。
     * 解决开学第一天可能不是周一（如周三开学或周日报道）导致的周次漂移问题。
     * 若开学日为周日（周七），高校通常为学生报到注册日，教学第 1 周周一应向后顺延对齐到下周一。
     */
    fun getAlignedSemesterStart(semesterStartDate: LocalDate, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): LocalDate {
        val aligned = if (firstDayOfWeek == DayOfWeek.MONDAY && semesterStartDate.dayOfWeek == DayOfWeek.SUNDAY) {
            semesterStartDate.plusDays(1)
        } else {
            val diff = (semesterStartDate.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
            semesterStartDate.minusDays(diff.toLong())
        }
        runCatching {
            android.util.Log.i(TAG, "[VivoDateAligner] 对齐开学周一: 原始开学日=$semesterStartDate(周${semesterStartDate.dayOfWeek.value}) -> 第1周周一=$aligned")
        }
        return aligned
    }

    /**
     * 精确计算某门课程在某一周次上课时的起止毫秒时间戳 (UTC Epoch Millis)
     */
    fun calculateLessonEpoch(
        alignedStartDate: LocalDate,
        week: Int,
        course: CourseItem,
        timeSlots: Map<Int, TimeSlot>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Pair<Long, Long>? {
        // 目标上课日期 = 基准周一 + (周次 - 1) 周 + (星期几 - 1) 天
        val dayOffset = (course.dayOfWeek.coerceIn(1, 7) - 1).toLong()
        val targetDate = alignedStartDate
            .plusWeeks((week - 1).toLong())
            .plusDays(dayOffset)

        // 1. 特殊整日/实训任务 (节次 0~0 或无效节次)
        if (course.startSection <= 0 || course.endSection <= 0) {
            val startZoned = LocalDateTime.of(targetDate, LocalTime.of(8, 30)).atZone(zoneId)
            val endZoned = LocalDateTime.of(targetDate, LocalTime.of(17, 30)).atZone(zoneId)
            return Pair(
                startZoned.toInstant().toEpochMilli(),
                endZoned.toInstant().toEpochMilli()
            )
        }

        // 2. 正常节次计算，带边界容错
        val startSlot = timeSlots[course.startSection]
            ?: timeSlots.values.minByOrNull { it.number }
            ?: return null
        val endSlot = timeSlots[course.endSection]
            ?: timeSlots.values.maxByOrNull { it.number }
            ?: return null

        val (sH, sM) = startSlot.startTime.split(":").map { it.toInt() }
        val (eH, eM) = endSlot.endTime.split(":").map { it.toInt() }

        val startZoned = LocalDateTime.of(targetDate, LocalTime.of(sH, sM)).atZone(zoneId)
        val endZoned = LocalDateTime.of(targetDate, LocalTime.of(eH, eM)).atZone(zoneId)

        return Pair(
            startZoned.toInstant().toEpochMilli(),
            endZoned.toInstant().toEpochMilli()
        )
    }
}
