package com.app.vivosync.parser

import com.app.vivosync.model.CourseItem
import com.app.vivosync.model.SyncPayload
import com.app.vivosync.model.TimeSlot
import java.time.LocalDate

object MockCourseData {

    /**
     * 标准高校 12 节课作息时间表
     */
    val defaultTimeSlots = listOf(
        TimeSlot(1, "08:00", "08:45"),
        TimeSlot(2, "08:55", "09:40"),
        TimeSlot(3, "10:00", "10:45"),
        TimeSlot(4, "10:55", "11:40"),
        TimeSlot(5, "14:00", "14:45"),
        TimeSlot(6, "14:55", "15:40"),
        TimeSlot(7, "16:00", "16:45"),
        TimeSlot(8, "16:55", "17:40"),
        TimeSlot(9, "19:00", "19:45"),
        TimeSlot(10, "19:55", "20:40"),
        TimeSlot(11, "20:50", "21:35"),
        TimeSlot(12, "21:45", "22:30")
    )

    /**
     * 示例大学课表
     */
    val sampleCourses = listOf(
        CourseItem(
            name = "高等数学",
            teacher = "张伟 教授",
            room = "教一 101",
            dayOfWeek = 1, // 周一
            startSection = 1,
            endSection = 2,
            weeks = (1..16).toList(),
            vivoColorIndex = 3 // 海浪蓝
        ),
        CourseItem(
            name = "大学物理",
            teacher = "李强 教授",
            room = "实验楼 302",
            dayOfWeek = 1, // 周一
            startSection = 5,
            endSection = 6,
            weeks = (1..16).toList(),
            vivoColorIndex = 1 // 薄荷绿
        ),
        CourseItem(
            name = "数据结构与算法",
            teacher = "王建国 博士",
            room = "信息楼 405",
            dayOfWeek = 2, // 周二
            startSection = 3,
            endSection = 4,
            weeks = (1..18).toList(),
            vivoColorIndex = 2 // 珊瑚橙
        ),
        CourseItem(
            name = "计算机网络",
            teacher = "陈明 讲师",
            room = "信东 201",
            dayOfWeek = 3, // 周三
            startSection = 1,
            endSection = 2,
            weeks = (1..16).toList(),
            vivoColorIndex = 8 // 天青蓝
        ),
        CourseItem(
            name = "操作系统",
            teacher = "刘洋 副教授",
            room = "教三 208",
            dayOfWeek = 3, // 周三
            startSection = 7,
            endSection = 8,
            weeks = (1..18).toList(),
            vivoColorIndex = 4 // 蔷薇紫
        ),
        CourseItem(
            name = "毛概理论与实践",
            teacher = "赵云 老师",
            room = "公教楼 102",
            dayOfWeek = 4, // 周四
            startSection = 3,
            endSection = 4,
            weeks = (1..16).toList(),
            vivoColorIndex = 10 // 暖日橙
        ),
        CourseItem(
            name = "专业英语与学术写作",
            teacher = "Sarah Smith",
            room = "外语楼 501",
            dayOfWeek = 5, // 周五
            startSection = 5,
            endSection = 6,
            weeks = (1..12).toList(),
            vivoColorIndex = 9 // 薰衣紫
        )
    )

    /**
     * 生成当前学期的默认测试 Payload
     */
    fun createSamplePayload(): SyncPayload {
        val today = LocalDate.now()
        val mondayOfThisWeek = today.minusDays((today.dayOfWeek.value - 1).toLong())

        return SyncPayload(
            semesterStartDate = mondayOfThisWeek,
            semesterTotalWeeks = 20,
            timeSlots = defaultTimeSlots,
            courses = sampleCourses,
            alarmMinutes = 15
        )
    }
}
