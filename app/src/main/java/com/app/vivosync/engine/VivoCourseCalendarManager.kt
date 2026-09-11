package com.app.vivosync.engine

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.net.toUri
import com.app.vivosync.guard.VivoEnvironmentGuard
import com.app.vivosync.model.VivoCourseCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * vivo OriginOS 原生课程表管理引擎
 * 针对 CalendarContract.Calendars 中的 "Course account" 执行增、删、查、激活切换
 */
class VivoCourseCalendarManager(private val context: Context) {

    private val resolver = context.contentResolver

    private val syncAdapterCalendarsUri: Uri
        get() = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, VivoEnvironmentGuard.COURSE_ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, VivoEnvironmentGuard.COURSE_ACCOUNT_TYPE)
            .build()

    private val syncAdapterEventsUri: Uri
        get() = CalendarContract.Events.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, VivoEnvironmentGuard.COURSE_ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, VivoEnvironmentGuard.COURSE_ACCOUNT_TYPE)
            .build()

    /**
     * 查询系统日历中所有属于 vivo Course account 的课程表
     */
    suspend fun getVivoCourseCalendars(): List<VivoCourseCalendar> = withContext(Dispatchers.IO) {
        if (!com.app.vivosync.permission.PermissionManager.hasCalendarPermissions(context)) {
            return@withContext emptyList()
        }

        val result = mutableListOf<VivoCourseCalendar>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            "cal_sync1",
            "cal_sync3"
        )
        val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?"
        val selectionArgs = arrayOf(VivoEnvironmentGuard.COURSE_ACCOUNT_NAME, VivoEnvironmentGuard.COURSE_ACCOUNT_TYPE)

        runCatching {
            resolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Calendars._ID} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val colorIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
                val sync1Index = cursor.getColumnIndex("cal_sync1")
                val sync3Index = cursor.getColumnIndex("cal_sync3")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex) ?: "课程表"
                    val color = cursor.getInt(colorIndex)
                    val sync1 = if (sync1Index >= 0) cursor.getString(sync1Index) else "0"
                    val sync3 = if (sync3Index >= 0) cursor.getInt(sync3Index) else 0

                    result.add(
                        VivoCourseCalendar(
                            id = id,
                            displayName = name,
                            color = color,
                            isActive = sync1 == "1",
                            semesterStartJulianDay = sync3,
                            courseCount = 0
                        )
                    )
                }
            }
        }
        val courseCounts = queryCourseCounts(result.mapTo(mutableSetOf()) { it.id })
        result.map { calendar ->
            calendar.copy(courseCount = courseCounts[calendar.id] ?: 0)
        }
    }

    /**
     * 统计指定日历下的有效课程门数
     *
     * 优先统计具备 RRULE 循环规则的基础事件记录数；
     * 若包含未定义循环规则的历史离散日程实例，则按课程标题进行去重统计。
     */
    private fun queryCourseCounts(calendarIds: Set<Long>): Map<Long, Int> {
        if (!com.app.vivosync.permission.PermissionManager.hasCalendarPermissions(context)) {
            return emptyMap()
        }
        if (calendarIds.isEmpty()) return emptyMap()

        val uri = CalendarContract.Events.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.RRULE
        )
        val placeholders = calendarIds.joinToString(",") { "?" }
        val selection = "${CalendarContract.Events.CALENDAR_ID} IN ($placeholders)"
        val selectionArgs = calendarIds.map(Long::toString).toTypedArray()

        return runCatching {
            resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val calendarIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
                val titleCol = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val rruleCol = cursor.getColumnIndex(CalendarContract.Events.RRULE)
                val stats = mutableMapOf<Long, CourseCountStats>()

                while (cursor.moveToNext()) {
                    val calendarId = cursor.getLong(calendarIdCol)
                    val calendarStats = stats.getOrPut(calendarId, ::CourseCountStats)
                    calendarStats.totalCount++
                    val title = if (titleCol != -1) cursor.getString(titleCol)?.trim().orEmpty() else ""
                    val rrule = if (rruleCol != -1) cursor.getString(rruleCol) else null
                    if (rrule.isNullOrBlank()) {
                        calendarStats.hasDiscreteEvents = true
                    }
                    if (title.isNotEmpty()) {
                        calendarStats.uniqueTitles.add(title)
                    }
                }

                stats.mapValues { (_, value) ->
                    if (value.hasDiscreteEvents && value.totalCount > value.uniqueTitles.size * 2 && value.uniqueTitles.isNotEmpty()) {
                        value.uniqueTitles.size
                    } else {
                        value.totalCount
                    }
                }
            } ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    private class CourseCountStats(
        var totalCount: Int = 0,
        var hasDiscreteEvents: Boolean = false,
        val uniqueTitles: MutableSet<String> = mutableSetOf()
    )

    /**
     * 在 vivo 系统日历中新建一个原生课程表
     * @param name 课表显示名称（如“2026春季学期”）
     * @param activate 是否立即在桌面原子组件中激活展示该课表
     * @return 新创建的 Calendar ID
     */
    suspend fun createVivoCourseCalendar(name: String, activate: Boolean = true): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, VivoEnvironmentGuard.COURSE_ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, VivoEnvironmentGuard.COURSE_ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, VivoEnvironmentGuard.COURSE_ACCOUNT_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, name.trim().ifEmpty { "课程表" })
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF3B82F6.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, VivoEnvironmentGuard.COURSE_ACCOUNT_NAME)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.VISIBLE, if (activate) 1 else 0)
            put("cal_sync1", if (activate) "1" else "0")
            put("cal_sync10", now)
        }

        val uri = resolver.insert(syncAdapterCalendarsUri, values)
            ?: throw IllegalStateException("创建 vivo 课程表日历失败")

        val newCalendarId = ContentUris.parseId(uri)

        if (activate) {
            switchActiveCourseCalendar(newCalendarId, autoOpenCalendar = true)
        } else {
            notifySystemAndWidgets()
        }

        newCalendarId
    }

    /**
     * 切换桌面原子组件中激活展示的 vivo 课程表
     * 将目标日历的 cal_sync1 置为 "1"、VISIBLE 置为 1，其余全部置为 "0"
     * 并通知 ContentObserver 及 OriginOS 桌面小组件刷新
     * @param autoOpenCalendar 是否同时通过 DeepLink 唤醒 vivo 日历回写内部私有配置
     */
    suspend fun switchActiveCourseCalendar(calendarId: Long, autoOpenCalendar: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val ops = ArrayList<ContentProviderOperation>()

        // 1. 将其他属于 Course account 的课程表置为未激活 (cal_sync1 = "0", visible = 0)
        ops.add(
            ContentProviderOperation.newUpdate(syncAdapterCalendarsUri)
                .withSelection(
                    "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars._ID} != ?",
                    arrayOf(VivoEnvironmentGuard.COURSE_ACCOUNT_NAME, VivoEnvironmentGuard.COURSE_ACCOUNT_TYPE, calendarId.toString())
                )
                .withValue("cal_sync1", "0")
                .withValue(CalendarContract.Calendars.VISIBLE, 0)
                .build()
        )

        // 2. 将指定 calendarId 的课程表置为激活 (cal_sync1 = "1", visible = 1, cal_sync10 = now)
        ops.add(
            ContentProviderOperation.newUpdate(syncAdapterCalendarsUri)
                .withSelection(
                    "${CalendarContract.Calendars._ID} = ?",
                    arrayOf(calendarId.toString())
                )
                .withValue("cal_sync1", "1")
                .withValue(CalendarContract.Calendars.VISIBLE, 1)
                .withValue("cal_sync10", now)
                .build()
        )

        val success = runCatching {
            val results = resolver.applyBatch(CalendarContract.AUTHORITY, ops)
            Log.d(TAG, "switchActiveCourseCalendar batch applied, operations=${results.size}")
            true
        }.onFailure { e ->
            Log.e(TAG, "fail to switchActiveCourseCalendar for id: $calendarId", e)
        }.getOrDefault(false)

        if (success) {
            notifySystemAndWidgets()
            if (autoOpenCalendar) {
                openInVivoCalendar(calendarId)
            }
        }
        success
    }

    /**
     * 通过 vivo 日历预留的 DeepLink 接口打开指定课程表
     * 协议：calendar://com.android.calendar/course?deepLinkCalendarId=<id>
     * 驱动 vivo 日历 CourseOverviewActivity 自动回写私有 SharedPreferences (preference_current_course_tab_calendar_id)
     */
    fun openInVivoCalendar(calendarId: Long): Boolean {
        return runCatching {
            val uri = "calendar://com.android.calendar/course?deepLinkCalendarId=$calendarId".toUri()
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.bbk.calendar")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "openInVivoCalendar deepLink launched for id: $calendarId")
            true
        }.onFailure { e ->
            Log.e(TAG, "fail to openInVivoCalendar for id: $calendarId", e)
        }.getOrDefault(false)
    }

    /**
     * 删除指定 vivo 课程表日历及其关联的全部日程与扩展属性
     */
    suspend fun deleteVivoCourseCalendar(calendarId: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Log.i(TAG, "开始删除 vivo 课程表日历 #$calendarId...")
            // 1. 先清理日程（使用 SyncAdapter URI，若抛安全异常则兜底常规删除）
            val deletedEvents = try {
                val count = resolver.delete(
                    syncAdapterEventsUri,
                    "${CalendarContract.Events.CALENDAR_ID} = ?",
                    arrayOf(calendarId.toString())
                )
                Log.i(TAG, "通过 syncAdapterEventsUri 清理日历 #$calendarId 日程: $count 条")
                count
            } catch (e: SecurityException) {
                Log.w(TAG, "syncAdapterEventsUri 删除抛安全异常: ${e.message}, 尝试标准 CONTENT_URI...")
                val count = resolver.delete(
                    CalendarContract.Events.CONTENT_URI,
                    "${CalendarContract.Events.CALENDAR_ID} = ?",
                    arrayOf(calendarId.toString())
                )
                Log.i(TAG, "通过标准 CONTENT_URI 清理日历 #$calendarId 日程: $count 条")
                count
            }

            // 2. 再安全删除日历
            val rows = resolver.delete(
                syncAdapterCalendarsUri,
                "${CalendarContract.Calendars._ID} = ?",
                arrayOf(calendarId.toString())
            )
            Log.i(TAG, "删除日历 #$calendarId 结果: 受影响日历行数=$rows, 已清理关联日程=$deletedEvents 条")

            if (rows > 0) {
                notifySystemAndWidgets()
                true
            } else {
                false
            }
        }.onFailure { e ->
            Log.e(TAG, "fail to deleteVivoCourseCalendar id: $calendarId", e)
        }.getOrDefault(false)
    }

    /**
     * 通知系统日历观察者与 OriginOS 桌面原子小组件刷新
     */
    fun notifySystemAndWidgets() {
        runCatching {
            // 1. 通知 ContentObserver
            resolver.notifyChange(CalendarContract.Calendars.CONTENT_URI, null)
            resolver.notifyChange(CalendarContract.Events.CONTENT_URI, null)

            // 2. 发送 OriginOS 桌面及日历小组件更新广播
            val actions = listOf(
                "android.appwidget.action.APPWIDGET_UPDATE",
                "com.vivo.action.calendar.PROVIDER_UPDATE",
                "com.vivo.calendar.widget_dataReport",
                "vivo.intent.action.APPWIDGET_UPDATE",
                "com.bbk.calendar.APPWIDGET_UPDATE"
            )
            for (action in actions) {
                val intent = Intent(action)
                intent.setPackage("com.bbk.calendar")
                context.sendBroadcast(intent)

                val globalIntent = Intent(action)
                context.sendBroadcast(globalIntent)
            }
            Log.d(TAG, "notifySystemAndWidgets broadcast sent successfully")
        }.onFailure { e ->
            Log.e(TAG, "fail to notifySystemAndWidgets", e)
        }
    }

    companion object {
        private const val TAG = "VivoCourseCalManager"
    }
}
