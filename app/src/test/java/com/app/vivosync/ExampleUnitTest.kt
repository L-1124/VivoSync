package com.app.vivosync

import com.app.vivosync.engine.VivoDateAligner
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testAlignedSemesterStart_sundayAlignsToNextMonday() {
        // 2026-09-06 is Sunday (周七)
        val sunday = LocalDate.of(2026, 9, 6)
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)

        val aligned = VivoDateAligner.getAlignedSemesterStart(sunday)
        // Should align to next Monday (2026-09-07)
        assertEquals(LocalDate.of(2026, 9, 7), aligned)
        assertEquals(DayOfWeek.MONDAY, aligned.dayOfWeek)
    }

    @Test
    fun testAlignedSemesterStart_mondayRemainsSame() {
        // 2026-09-07 is Monday
        val monday = LocalDate.of(2026, 9, 7)
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)

        val aligned = VivoDateAligner.getAlignedSemesterStart(monday)
        assertEquals(LocalDate.of(2026, 9, 7), aligned)
    }

    @Test
    fun testAlignedSemesterStart_midweekAlignsToCurrentWeekMonday() {
        // 2026-09-09 is Wednesday
        val wednesday = LocalDate.of(2026, 9, 9)
        assertEquals(DayOfWeek.WEDNESDAY, wednesday.dayOfWeek)

        val aligned = VivoDateAligner.getAlignedSemesterStart(wednesday)
        assertEquals(LocalDate.of(2026, 9, 7), aligned)
    }

    @Test
    fun testDefaultScheduleName() {
        val sepDate = LocalDate.of(2026, 9, 6)
        val marDate = LocalDate.of(2026, 3, 1)
        val janDate = LocalDate.of(2027, 1, 15)

        fun gen(d: LocalDate): String {
            val sem = if (d.monthValue in 8..12 || d.monthValue == 1) "秋季学期" else "春季学期"
            return "${d.year}年$sem"
        }

        assertEquals("2026年秋季学期", gen(sepDate))
        assertEquals("2026年春季学期", gen(marDate))
        assertEquals("2027年秋季学期", gen(janDate))
    }

    @Test
    fun testVivoRruleGeneration_matchesOfficialCalendar() {
        // 对齐真机日历 #9 (大一上) 的真实验证用例：
        // 开学日期: 2025-09-08 (周一), 总周数: 19, 上课日: 周一 (1), 周次: 6..17 (跨年周次)
        val semesterStart = LocalDate.of(2025, 9, 8)
        val weeks = (6..17).toList()
        val shanghaiZone = java.time.ZoneId.of("Asia/Shanghai")

        val rrule = com.app.vivosync.engine.VivoNativeProviderSyncClient.buildVivoRrule(
            weeks = weeks,
            dayOfWeek = 1,
            alignedStartDate = semesterStart,
            totalWeeks = 19,
            zoneId = shanghaiZone
        )

        val expected = "FREQ=WEEKLY;UNTIL=20260119T000000;WKST=SU;BYDAY=MO;BYWEEKNO=42,43,44,45,46,47,48,49,50,51,52,1"
        assertEquals(expected, rrule)
    }
}