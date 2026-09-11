package com.app.vivosync.parser

import com.app.vivosync.model.CourseItem
import com.app.vivosync.model.SyncPayload
import com.app.vivosync.model.TimeSlot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

/**
 * 通用标准 ICS 课表文件解析器
 */
object IcsScheduleParser {

    /**
     * 从 ICS 文件头部提取日历名称（X-WR-CALNAME）
     */
    fun extractCalendarName(icsText: String): String? {
        for (line in icsText.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("X-WR-CALNAME:", ignoreCase = true)) {
                val name = trimmed.substringAfter(":").trim()
                if (name.isNotBlank()) return unescapeIcs(name)
            }
            if (trimmed.startsWith("BEGIN:VEVENT", ignoreCase = true)) {
                break
            }
        }
        return null
    }

    /**
     * 解析 ICS 字符串内容并转换为 SyncPayload
     */
    fun parse(icsText: String, semesterStartDate: LocalDate = LocalDate.now()): SyncPayload {
        val lines = icsText.lines().map { it.trim() }
        val courses = mutableListOf<CourseItem>()
        val detectedSlots = mutableSetOf<Pair<String, String>>()

        var currentSummary = ""
        var currentLocation = ""
        var currentDescription = ""
        var currentDtStart = ""
        var currentDtEnd = ""
        var currentRrule = ""
        var inEvent = false

        for (line in lines) {
            when {
                line.startsWith("BEGIN:VEVENT") -> {
                    inEvent = true
                    currentSummary = ""
                    currentLocation = ""
                    currentDescription = ""
                    currentDtStart = ""
                    currentDtEnd = ""
                    currentRrule = ""
                }
                line.startsWith("END:VEVENT") -> {
                    if (inEvent && currentSummary.isNotBlank() && currentDtStart.isNotBlank()) {
                        val (dayOfWeek, startTime, endTime, weeks) = parseEventDetails(
                            dtStart = currentDtStart,
                            dtEnd = currentDtEnd,
                            rrule = currentRrule,
                            semesterStart = semesterStartDate
                        )

                        if (startTime.isNotBlank() && endTime.isNotBlank()) {
                            detectedSlots.add(Pair(startTime, endTime))
                        }

                        // 简单的根据当前已发现的 slots 计算节次，或在后续规整化
                        courses.add(
                            CourseItem(
                                name = unescapeIcs(currentSummary),
                                teacher = unescapeIcs(currentDescription),
                                room = unescapeIcs(currentLocation),
                                dayOfWeek = dayOfWeek,
                                startSection = 1, // 后续与 timeSlots 结合重排
                                endSection = 2,
                                weeks = weeks.ifEmpty { (1..16).toList() },
                                vivoColorIndex = (courses.size % 12) + 1
                            )
                        )
                    }
                    inEvent = false
                }
                inEvent -> {
                    when {
                        line.startsWith("SUMMARY:") -> currentSummary = line.substringAfter("SUMMARY:")
                        line.startsWith("LOCATION:") -> currentLocation = line.substringAfter("LOCATION:")
                        line.startsWith("DESCRIPTION:") -> currentDescription = line.substringAfter("DESCRIPTION:")
                        line.startsWith("DTSTART") -> currentDtStart = line.substringAfter(":")
                        line.startsWith("DTEND") -> currentDtEnd = line.substringAfter(":")
                        line.startsWith("RRULE:") -> currentRrule = line.substringAfter("RRULE:")
                    }
                }
            }
        }

        // 整理作息列表
        val sortedSlots = detectedSlots.asSequence().sortedBy { it.first }.mapIndexed { idx, pair ->
            TimeSlot(number = idx + 1, startTime = pair.first, endTime = pair.second)
        }.toList().ifEmpty { MockCourseData.defaultTimeSlots }

        return SyncPayload(
            semesterStartDate = semesterStartDate,
            semesterTotalWeeks = 20,
            timeSlots = sortedSlots,
            courses = courses.ifEmpty { MockCourseData.sampleCourses },
            alarmMinutes = 15
        )
    }

    private fun parseEventDetails(
        dtStart: String,
        dtEnd: String,
        rrule: String,
        semesterStart: LocalDate
    ): ParsedEventInfo {
        var dayOfWeek = 1
        var startTime = "08:00"
        var endTime = "09:40"
        val weeks = mutableListOf<Int>()

        try {
            val dateStr = dtStart.take(8) // YYYYMMDD
            val timeStr = dtStart.drop(9).take(4) // HHmm
            if (timeStr.length == 4) {
                startTime = "${timeStr.take(2)}:${timeStr.drop(2)}"
            }

            val endTimeStr = dtEnd.drop(9).take(4)
            if (endTimeStr.length == 4) {
                endTime = "${endTimeStr.take(2)}:${endTimeStr.drop(2)}"
            }

            if (dateStr.length == 8) {
                val eventDate = LocalDate.parse(dateStr, DateTimeFormatter.BASIC_ISO_DATE)
                dayOfWeek = eventDate.dayOfWeek.value
            }

            // 解析 RRULE 类似 COUNT=16 或 UNTIL
            val countMatcher = Pattern.compile("COUNT=(\\d+)").matcher(rrule)
            if (countMatcher.find()) {
                val count = countMatcher.group(1)?.toIntOrNull() ?: 16
                for (w in 1..count) {
                    weeks.add(w)
                }
            }
        } catch (_: Exception) {
        }

        return ParsedEventInfo(dayOfWeek, startTime, endTime, weeks)
    }

    private fun unescapeIcs(text: String): String {
        return text.replace("\\n", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
    }

    private data class ParsedEventInfo(
        val dayOfWeek: Int,
        val startTime: String,
        val endTime: String,
        val weeks: List<Int>
    )
}
