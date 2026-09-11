package com.app.vivosync.model

import kotlinx.serialization.Serializable

/**
 * 对应 vivo OriginOS 系统日历（CalendarContract.Calendars）中的原生课程表
 * 账户为：ACCOUNT_NAME = "Course account", ACCOUNT_TYPE = "LOCAL"
 */
@Serializable
data class VivoCourseCalendar(
    val id: Long,
    val displayName: String,
    val color: Int = 0,
    val isActive: Boolean = false,
    val semesterStartJulianDay: Int = 0,
    val courseCount: Int = 0
)
