package com.app.vivosync.engine

import android.util.Log
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.JulianFields
import java.util.regex.Pattern

/**
 * 拾光课表与教务系统日期智能容错解析器及 vivo 儒略日转换工具
 */
object VivoDateParser {
    private const val TAG = "VivoSync_Date"

    // 匹配 YYYY-M-D, YYYY/M/D, YYYY.M.D, YYYY年M月D日 等多种教务格式
    private val DATE_REGEX = Pattern.compile("^(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?.*$")

    /**
     * 智能容错解析教务系统或拾光脚本传回的开学日期字符串
     */
    fun parseSemesterStartDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) {
            Log.w(TAG, "[DateParser] 输入开学日期为空或 null")
            return null
        }
        val trimmed = raw.trim()

        // 1. 纯数字时间戳格式（毫秒 13 位 或 秒 10 位）
        if (trimmed.all { it.isDigit() }) {
            val epoch = trimmed.toLongOrNull()
            if (epoch != null) {
                val epochMillis = if (trimmed.length <= 10) epoch * 1000L else epoch
                val result = runCatching {
                    Instant.ofEpochMilli(epochMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                }.getOrNull()
                if (result != null) {
                    Log.i(TAG, "[DateParser] 解析时间戳成功: raw='$trimmed' -> $result (周${result.dayOfWeek.value})")
                    return result
                }
            }
        }

        // 2. 正则格式匹配 YYYY[-/.年]M[-/.月]D
        val matcher = DATE_REGEX.matcher(trimmed)
        if (matcher.find()) {
            val year = matcher.group(1)?.toIntOrNull()
            val month = matcher.group(2)?.toIntOrNull()
            val day = matcher.group(3)?.toIntOrNull()
            if ((year != null) && (month != null) && (day != null)) {
                val result = runCatching {
                    LocalDate.of(year, month, day)
                }.getOrNull()
                if (result != null) {
                    Log.i(TAG, "[DateParser] 正则解析日期成功: raw='$trimmed' -> $result (周${result.dayOfWeek.value})")
                    return result
                }
            }
        }

        // 3. Fallback: 尝试 ISO 标准 LocalDate.parse
        return runCatching {
            val date = LocalDate.parse(trimmed)
            Log.i(TAG, "[DateParser] ISO解析成功: raw='$trimmed' -> $date (周${date.dayOfWeek.value})")
            date
        }.onFailure {
            Log.e(TAG, "[DateParser] 无法解析开学日期: '$raw', 错误: ${it.message}")
        }.getOrNull()
    }

    /**
     * LocalDate 转 vivo 日历 cal_sync3 儒略日 (Julian Day)
     * 与 vivo 原生日历 w0.java 算法完全对齐 (Epoch day 0 = 2440588)
     */
    fun toJulianDay(date: LocalDate): Int {
        val jd = date.getLong(JulianFields.JULIAN_DAY).toInt()
        Log.d(TAG, "[JulianDay] LocalDate($date) -> JulianDay: $jd")
        return jd
    }
}
