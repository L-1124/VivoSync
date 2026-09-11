package com.app.vivosync.engine

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import com.app.vivosync.guard.VivoEnvironmentGuard
import com.app.vivosync.model.CourseItem
import com.app.vivosync.model.SyncPayload
import com.app.vivosync.model.TimeSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * vivo 系统日历课程同步客户端
 *
 * 负责通过 [CalendarContract] 执行课程日历及其日程事件的底层持久化与事务维护。
 * 遵循 RFC 5545 (iCalendar) 周期性事件标准：每门课程写入包含周循环规则 (RRULE) 的基础事件记录，
 * 结合 [CalendarContract.ExtendedProperties] 与 [CalendarContract.Reminders] 维护节次与提醒元数据。
 */
class VivoNativeProviderSyncClient(private val context: Context) {

    private val calendarManager = VivoCourseCalendarManager(context)

    /**
     * 为日历 Provider URI 构造 SyncAdapter 授权参数，确保底层通道畅通
     * 声明 caller_is_syncadapter=true, account_name 与 account_type=LOCAL
     */
    private fun asVivoSyncAdapter(
        uri: Uri,
        accountName: String = VivoEnvironmentGuard.COURSE_ACCOUNT_NAME,
        accountType: String = VivoEnvironmentGuard.COURSE_ACCOUNT_TYPE
    ): Uri {
        return uri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, accountType)
            .build()
    }

    data class CalendarAccountInfo(
        val id: Long,
        val accountName: String,
        val accountType: String,
        val displayName: String
    )

    /**
     * 查询指定日历的底层账户与类型信息
     */
    fun queryCalendarAccountInfo(calendarId: Long): CalendarAccountInfo? {
        if (!com.app.vivosync.permission.PermissionManager.hasCalendarPermissions(context)) {
            return null
        }
        val uri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
        val selection = "${CalendarContract.Calendars._ID} = ?"
        val selectionArgs = arrayOf(calendarId.toString())

        return runCatching {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    CalendarAccountInfo(
                        id = cursor.getLong(0),
                        accountName = cursor.getString(1) ?: VivoEnvironmentGuard.COURSE_ACCOUNT_NAME,
                        accountType = cursor.getString(2) ?: VivoEnvironmentGuard.COURSE_ACCOUNT_TYPE,
                        displayName = cursor.getString(3) ?: "Unknown"
                    )
                } else null
            }
        }.getOrNull()
    }

    /**
     * 安全删除系统中的指定日历（以 SyncAdapter 授权执行硬删除，系统会自动级联删除该日历下的所有日程、提醒和节次属性）
     */
    fun deleteCalendar(calendarId: Long): Result<Boolean> = runCatching {
        if (!com.app.vivosync.permission.PermissionManager.hasCalendarPermissions(context)) {
            throw SecurityException("无日历权限，无法删除日历")
        }
        val calInfo = queryCalendarAccountInfo(calendarId)
        val accountName = calInfo?.accountName ?: VivoEnvironmentGuard.COURSE_ACCOUNT_NAME
        val accountType = calInfo?.accountType ?: VivoEnvironmentGuard.COURSE_ACCOUNT_TYPE

        val baseUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId)
        val deleteUri = asVivoSyncAdapter(baseUri, accountName, accountType)
        val rows = context.contentResolver.delete(deleteUri, null, null)
        Log.i(TAG, "安全删除日历 #$calendarId ('${calInfo?.displayName}') 结果: 影响行数=$rows")
        rows > 0
    }

    /**
     * 统计指定日历中的当前日程总数
     */
    fun queryTotalEventsCount(calendarId: Long): Int {
        if (!com.app.vivosync.permission.PermissionManager.hasCalendarPermissions(context)) {
            return 0
        }
        val uri = CalendarContract.Events.CONTENT_URI
        val projection = arrayOf(CalendarContract.Events._ID)
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ?"
        val selectionArgs = arrayOf(calendarId.toString())
        return runCatching {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                cursor.count
            } ?: 0
        }.getOrDefault(0)
    }

    /**
     * 打印目标日历中的已有日程元数据样本供调试排查
     */
    private fun logExistingEventsSummary(calendarId: Long) {
        if (!com.app.vivosync.permission.PermissionManager.hasCalendarPermissions(context)) return
        val uri = CalendarContract.Events.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.ORGANIZER,
            CalendarContract.Events.CUSTOM_APP_PACKAGE,
            "sync_data8",
            CalendarContract.Events.DTSTART
        )
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ?"
        val selectionArgs = arrayOf(calendarId.toString())

        runCatching {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val total = cursor.count
                Log.i(TAG, "[日志探针] 日历 #$calendarId 当前总日程数: $total 条")
                var printed = 0
                val idCol = cursor.getColumnIndex(CalendarContract.Events._ID)
                val titleCol = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val orgCol = cursor.getColumnIndex(CalendarContract.Events.ORGANIZER)
                val pkgCol = cursor.getColumnIndex(CalendarContract.Events.CUSTOM_APP_PACKAGE)
                val sync8Col = cursor.getColumnIndex("sync_data8")
                val startCol = cursor.getColumnIndex(CalendarContract.Events.DTSTART)

                while (cursor.moveToNext() && printed < 5) {
                    val id = if (idCol >= 0) cursor.getLong(idCol) else -1
                    val title = if (titleCol >= 0) cursor.getString(titleCol) else ""
                    val org = if (orgCol >= 0) cursor.getString(orgCol) else ""
                    val pkg = if (pkgCol >= 0) cursor.getString(pkgCol) else ""
                    val sync8 = if (sync8Col >= 0) cursor.getString(sync8Col) else ""
                    val dtStart = if (startCol >= 0) cursor.getLong(startCol) else 0L
                    Log.i(TAG, "[日志探针] 样本旧日程#$printed -> ID=$id, 标题='$title', ORGANIZER='$org', PKG='$pkg', sync_data8='$sync8', DTSTART=$dtStart")
                    printed++
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "[日志探针] 查询旧日程样本异常: ${e.message}")
        }
    }

    /**
     * 跨时段节次拆分
     *
     * 当课程跨越不同作息区间（如上午与下午之间的午休间隔）时，将其拆分为连续的时段区间日程，
     * 以保证日历排版与视图渲染的时间准确性。
     */
    private fun splitCourseByTimeSlots(course: CourseItem, timeSlotMap: Map<Int, TimeSlot>): List<CourseItem> {
        if (course.startSection <= 0 || course.endSection <= 0 || course.startSection == course.endSection || timeSlotMap.isEmpty()) {
            return listOf(course)
        }

        val sections = (course.startSection..course.endSection).toList()
        val groups = mutableListOf<MutableList<Int>>()
        var currentGroup = mutableListOf<Int>()
        var currentType: Int? = null

        for (sec in sections) {
            val type = timeSlotMap[sec]?.getTimeSlotType() ?: 1
            if (currentType == null || currentType == type) {
                currentGroup.add(sec)
                currentType = type
            } else {
                groups.add(currentGroup)
                currentGroup = mutableListOf(sec)
                currentType = type
            }
        }
        if (currentGroup.isNotEmpty()) {
            groups.add(currentGroup)
        }

        if (groups.size <= 1) {
            return listOf(course)
        }

        return groups.map { group ->
            course.copy(
                startSection = group.first(),
                endSection = group.last()
            )
        }
    }

    /**
     * 构建课程批量写入操作事务
     *
     * 包含以下数据表的原子操作：
     * 1. [CalendarContract.Events]：写入周循环事件（包含 RRULE、DURATION、时区等参数，dtend 为 null）
     * 2. [CalendarContract.ExtendedProperties]：写入课程节次范围 (lesson_start, lesson_end) 及提醒类型
     * 3. [CalendarContract.Reminders]：写入事件关联的课前提醒
     */
    private fun buildCourseSyncOperations(
        calendarId: Long,
        payload: SyncPayload,
        alignedStartDate: LocalDate,
        timeSlotMap: Map<Int, TimeSlot>,
        zoneId: ZoneId,
        accountName: String,
        accountType: String,
        ops: ArrayList<ContentProviderOperation>
    ): Pair<ArrayList<ContentProviderOperation>, Int> {
        val eventsUri = asVivoSyncAdapter(CalendarContract.Events.CONTENT_URI, accountName, accountType)
        val extendedPropsUri = asVivoSyncAdapter(CalendarContract.ExtendedProperties.CONTENT_URI, accountName, accountType)
        val remindersUri = asVivoSyncAdapter(CalendarContract.Reminders.CONTENT_URI, accountName, accountType)
        var insertedEventCount = 0

        val processedCourses = payload.courses.flatMap { course ->
            splitCourseByTimeSlots(course, timeSlotMap)
        }

        processedCourses.forEach { course ->
            val sortedWeeks = course.weeks.ifEmpty { (1..payload.semesterTotalWeeks).toList() }.distinct().sorted()
            val firstWeek = sortedWeeks.firstOrNull() ?: 1

            val epochPair = VivoDateAligner.calculateLessonEpoch(
                alignedStartDate = alignedStartDate,
                week = firstWeek,
                course = course,
                timeSlots = timeSlotMap,
                zoneId = zoneId
            ) ?: run {
                Log.w(TAG, "[buildOps] 无法计算课程时间戳: ${course.name}, 首周=$firstWeek, 节次=${course.startSection}~${course.endSection}")
                return@forEach
            }

            val (startMillis, endMillis) = epochPair
            val durationSeconds = ((endMillis - startMillis) / 1000).coerceAtLeast(60)
            val durationStr = "P${durationSeconds}S"

            val rrule = buildVivoRrule(
                weeks = sortedWeeks,
                dayOfWeek = course.dayOfWeek,
                alignedStartDate = alignedStartDate,
                totalWeeks = payload.semesterTotalWeeks,
                zoneId = zoneId
            )

            if (insertedEventCount < 8) {
                Log.d(TAG, "[Sync-RecurringEvent#$insertedEventCount] ${course.name} | 周${course.dayOfWeek} (${course.startSection}~${course.endSection}节) | 首周=$firstWeek, 时长=$durationStr | RRULE: $rrule")
            }

            val eventBackrefIndex = ops.size

            // 1. 插入 Events 表循环事件（包含 RRULE 与 DURATION，dtend 为 null）
            ops.add(
                ContentProviderOperation.newInsert(eventsUri)
                    .withValue(CalendarContract.Events.CALENDAR_ID, calendarId)
                    .withValue(CalendarContract.Events.ORGANIZER, VivoEnvironmentGuard.COURSE_ACCOUNT_NAME)
                    .withValue(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
                    .withValue(CalendarContract.Events.TITLE, course.name)
                    .withValue(CalendarContract.Events.EVENT_LOCATION, course.room)
                    .withValue(CalendarContract.Events.DESCRIPTION, course.teacher)
                    .withValue(CalendarContract.Events.DTSTART, startMillis)
                    .withValue(CalendarContract.Events.DTEND, null)
                    .withValue(CalendarContract.Events.DURATION, durationStr)
                    .withValue(CalendarContract.Events.RRULE, rrule)
                    .withValue(CalendarContract.Events.EVENT_TIMEZONE, zoneId.id)
                    .withValue(CalendarContract.Events.ALL_DAY, 0)
                    .withValue(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                    .withValue(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
                    .withValue(CalendarContract.Events.EVENT_COLOR_KEY, course.vivoColorIndex.toString())
                    .withValue("eventColor_index", course.vivoColorIndex)
                    .withValue(CalendarContract.Events.HAS_ALARM, if (payload.alarmMinutes in 0..60) 1 else 0)
                    .withValue("hasAttendeeData", 1)
                    .withValue("sync_data8", System.currentTimeMillis().toString())
                    .build()
            )

            // 2. 插入 ExtendedProperties 课程节次起止 (lesson_start)
            ops.add(
                ContentProviderOperation.newInsert(extendedPropsUri)
                    .withValueBackReference(CalendarContract.ExtendedProperties.EVENT_ID, eventBackrefIndex)
                    .withValue(CalendarContract.ExtendedProperties.NAME, "lesson_start")
                    .withValue(CalendarContract.ExtendedProperties.VALUE, course.startSection.toString())
                    .build()
            )

            // 3. 插入 ExtendedProperties 课程节次起止 (lesson_end)
            ops.add(
                ContentProviderOperation.newInsert(extendedPropsUri)
                    .withValueBackReference(CalendarContract.ExtendedProperties.EVENT_ID, eventBackrefIndex)
                    .withValue(CalendarContract.ExtendedProperties.NAME, "lesson_end")
                    .withValue(CalendarContract.ExtendedProperties.VALUE, course.endSection.toString())
                    .build()
            )

            // 4. 插入 ExtendedProperties 提醒类型 (reminder_alert_type)
            ops.add(
                ContentProviderOperation.newInsert(extendedPropsUri)
                    .withValueBackReference(CalendarContract.ExtendedProperties.EVENT_ID, eventBackrefIndex)
                    .withValue(CalendarContract.ExtendedProperties.NAME, "reminder_alert_type")
                    .withValue(CalendarContract.ExtendedProperties.VALUE, "0")
                    .build()
            )

            // 5. 插入 Reminders 表事件提醒
            if (payload.alarmMinutes in 0..60) {
                ops.add(
                    ContentProviderOperation.newInsert(remindersUri)
                        .withValueBackReference(CalendarContract.Reminders.EVENT_ID, eventBackrefIndex)
                        .withValue(CalendarContract.Reminders.MINUTES, payload.alarmMinutes)
                        .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                        .build()
                )
            }

            insertedEventCount++
        }
        return Pair(ops, insertedEventCount)
    }

    private fun buildAtomicSyncOperations(
        calendarId: Long,
        payload: SyncPayload,
        alignedStartDate: LocalDate,
        timeSlotMap: Map<Int, TimeSlot>,
        zoneId: ZoneId,
        julianDay: Int,
        accountName: String,
        accountType: String
    ): Pair<ArrayList<ContentProviderOperation>, Int> {
        val calendarUri = asVivoSyncAdapter(
            ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId),
            accountName,
            accountType
        )
        val eventsUri = asVivoSyncAdapter(CalendarContract.Events.CONTENT_URI, accountName, accountType)
        val operations = arrayListOf(
            ContentProviderOperation.newUpdate(calendarUri)
                .withValue(CAL_SYNC_START_JULIAN_DAY, julianDay)
                .withValue(CAL_SYNC_UPDATED_AT, System.currentTimeMillis())
                .build(),
            ContentProviderOperation.newDelete(eventsUri)
                .withSelection(
                    "${CalendarContract.Events.CALENDAR_ID} = ?",
                    arrayOf(calendarId.toString())
                )
                .build()
        )
        return buildCourseSyncOperations(
            calendarId = calendarId,
            payload = payload,
            alignedStartDate = alignedStartDate,
            timeSlotMap = timeSlotMap,
            zoneId = zoneId,
            accountName = accountName,
            accountType = accountType,
            ops = operations
        )
    }

    /**
     * 批量事务静默刷入课程（包含 Events 日程、ExtendedProperties 节次绑定、Reminders 闹钟）
     * 完全后台执行，零弹窗交互
     */
    suspend fun syncCoursesSilent(calendarId: Long, payload: SyncPayload): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val effectiveTimeSlots = payload.timeSlots.ifEmpty { DEFAULT_TIME_SLOTS }
            val timeSlotMap = effectiveTimeSlots.associateBy { it.number }
            Log.i(TAG, "生效节次作息配置数量: ${timeSlotMap.size} 节 (首节: ${timeSlotMap[1]?.startTime}, 末节: ${timeSlotMap.values.maxByOrNull { it.number }?.endTime})")

            Log.i(TAG, "==================== 开始写入 vivo 课程表 ====================")
            Log.i(TAG, "目标日历 ID: $calendarId, 待同步课程总数: ${payload.courses.size}")
            Log.i(TAG, "学期开学日期(semesterStartDate): ${payload.semesterStartDate}, 总周数: ${payload.semesterTotalWeeks}")

            // 对齐到学期基准第 1 周周一
            val alignedStartDate = VivoDateAligner.getAlignedSemesterStart(payload.semesterStartDate)
            val newJulianDay = VivoDateParser.toJulianDay(alignedStartDate)
            Log.i(TAG, "计算得出的第 1 周周一(alignedStartDate): $alignedStartDate, 对应新儒略日(cal_sync3): $newJulianDay")

            val zoneId = ZoneId.systemDefault()
            val calInfo = queryCalendarAccountInfo(calendarId)
            val calAccountName = calInfo?.accountName ?: VivoEnvironmentGuard.COURSE_ACCOUNT_NAME
            val calAccountType = calInfo?.accountType ?: VivoEnvironmentGuard.COURSE_ACCOUNT_TYPE
            Log.i(TAG, "目标日历信息: ID=$calendarId, 名称='${calInfo?.displayName}', 账户名='$calAccountName', 账户类型='$calAccountType'")

            val countBeforeDelete = queryTotalEventsCount(calendarId)
            Log.i(TAG, "清理前目标日历内旧日程总数: $countBeforeDelete 条")
            if (countBeforeDelete > 0) {
                logExistingEventsSummary(calendarId)
            }

            val accountCandidates = listOf(
                calAccountName to calAccountType,
                VivoEnvironmentGuard.COURSE_ACCOUNT_NAME to VivoEnvironmentGuard.COURSE_ACCOUNT_TYPE
            ).distinct()
            var insertedCount = 0
            var applied = false
            var lastSecurityException: SecurityException? = null

            for ((accountName, accountType) in accountCandidates) {
                val (operations, eventCount) = buildAtomicSyncOperations(
                    calendarId = calendarId,
                    payload = payload,
                    alignedStartDate = alignedStartDate,
                    timeSlotMap = timeSlotMap,
                    zoneId = zoneId,
                    julianDay = newJulianDay,
                    accountName = accountName,
                    accountType = accountType
                )
                Log.i(TAG, "原子事务构建完成，账户='$accountName/$accountType', 日程=$eventCount, 操作=${operations.size}")
                try {
                    val batchResults = resolver.applyBatch(CalendarContract.AUTHORITY, operations)
                    Log.i(TAG, "原子 applyBatch 执行成功，返回结果条数: ${batchResults.size}")
                    insertedCount = eventCount
                    applied = true
                    break
                } catch (e: SecurityException) {
                    lastSecurityException = e
                    Log.w(TAG, "账户 '$accountName/$accountType' 原子事务遇到安全异常: ${e.message}")
                }
            }

            if (!applied) throw lastSecurityException ?: IllegalStateException("课表原子事务未执行")

            calendarManager.notifySystemAndWidgets()
            Log.i(TAG, "课表原子写入成功，已通知桌面原子小组件与系统日历刷新！")

            val postInsertTotal = queryTotalEventsCount(calendarId)
            Log.i(TAG, "==================== [同步完成] 写入后日历最终日程总数: $postInsertTotal 条 ====================")
            insertedCount
        }.onFailure { e ->
            Log.e(TAG, "课表静默写入过程发生异常: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "VivoSync_Sync"
        private const val CAL_SYNC_START_JULIAN_DAY = "cal_sync3"
        private const val CAL_SYNC_UPDATED_AT = "cal_sync10"

        /**
         * 生成符合 RFC 5545 规范的周循环规则字符串 (RRULE)
         *
         * 将课程学期周次映射为自然年周序号 (BYWEEKNO)，以支持跨年学期的周期性事件表达。
         * 格式规范：FREQ=WEEKLY;UNTIL=YYYYMMDDTHHMMSS;WKST=SU;BYDAY=MO;BYWEEKNO=42,43,...
         */
        fun buildVivoRrule(
            weeks: List<Int>,
            dayOfWeek: Int,
            alignedStartDate: LocalDate,
            totalWeeks: Int,
            zoneId: ZoneId = ZoneId.systemDefault()
        ): String {
            val cal = GregorianCalendar(TimeZone.getTimeZone(zoneId))
            cal.set(alignedStartDate.year, alignedStartDate.monthValue - 1, alignedStartDate.dayOfMonth, 0, 0, 0)
            val iC = cal.get(Calendar.WEEK_OF_YEAR)
            cal.set(Calendar.MONTH, 11) // 12月
            cal.set(Calendar.DAY_OF_MONTH, 25)
            val iC2 = cal.get(Calendar.WEEK_OF_YEAR)

            val sortedWeeks = weeks.distinct().sorted()
            val byWeekNoList = sortedWeeks.map { w ->
                if ((iC + w - 1) > iC2) {
                    (w + iC) - iC2 - 1
                } else {
                    (w + iC) - 1
                }
            }
            val byWeekNoStr = byWeekNoList.joinToString(",")

            val untilDate = alignedStartDate.plusWeeks(totalWeeks.toLong())
            val untilStr = untilDate.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'000000"))

            val byDayStr = when (dayOfWeek.coerceIn(1, 7)) {
                1 -> "MO"
                2 -> "TU"
                3 -> "WE"
                4 -> "TH"
                5 -> "FR"
                6 -> "SA"
                7 -> "SU"
                else -> "MO"
            }

            return "FREQ=WEEKLY;UNTIL=$untilStr;WKST=SU;BYDAY=$byDayStr;BYWEEKNO=$byWeekNoStr"
        }

        val DEFAULT_TIME_SLOTS = listOf(
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
            TimeSlot(12, "21:45", "22:30"),
            TimeSlot(13, "22:35", "23:20"),
            TimeSlot(14, "23:25", "00:10")
        )
    }
}
