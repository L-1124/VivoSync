package com.app.vivosync.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.app.vivosync.model.TimeSlot
import com.app.vivosync.model.VivoCourseCalendar
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.security.MessageDigest

/**
 * 官方预览导出生命周期跟踪结果
 */
sealed interface PendingExportResult {
    /** 当前无正在等待确认的导出操作 */
    data object NoPending : PendingExportResult
    /** 用户已在 vivo 官方界面成功点击保存并生成了新日历 */
    data class Success(val calendar: VivoCourseCalendar) : PendingExportResult
    /** 未检测到新日历，判定用户已取消或放弃保存 */
    data object Cancelled : PendingExportResult
}

/**
 * 本地课表作息指纹持久化仓库
 * 负责通过 Jetpack DataStore 记录日历 ID 与作息配置指纹（SHA-256）的绑定映射，
 * 并提供唤起 vivo 官方预览前后的快照比对与自动绑定能力。
 */
interface CalendarSlotBindingRepository {
    /**
     * 计算指定作息配置的 SHA-256 哈希指纹
     * 只要节次数量、起始时间或结束时间发生任何变化，指纹即刻改变
     */
    fun calculateFingerprint(timeSlots: List<TimeSlot>): String

    /**
     * 读取指定日历已绑定的作息指纹（若未绑定返回 null）
     */
    suspend fun getBinding(calendarId: Long): String?

    /**
     * 保存日历 ID、课表显示名称与作息指纹的绑定关系
     */
    suspend fun bind(calendarId: Long, calendarName: String, fingerprint: String)

    /**
     * 移除指定日历的作息指纹绑定
     */
    suspend fun unbind(calendarId: Long)

    /**
     * 校验日历当前绑定的作息指纹是否与待导入的 timeSlots 完全一致
     */
    suspend fun isSlotMatching(calendarId: Long, timeSlots: List<TimeSlot>): Boolean

    /**
     * 记录待导出状态（记录唤起官方预览前的日历 ID 集合快照、目标课表名称与作息指纹）
     */
    suspend fun setPendingExport(existingCalendarIds: Set<Long>, targetName: String, fingerprint: String)

    /**
     * 比对当前日历列表快照，检测官方预览导出是否成功保存
     */
    suspend fun checkPendingExportResult(currentCalendars: List<VivoCourseCalendar>): PendingExportResult

    /**
     * 清理待绑定的临时导出状态
     */
    suspend fun clearPendingBinding()
}

private val Context.slotBindingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vivosync_slot_bindings"
)

class CalendarSlotBindingRepositoryImpl(
    private val context: Context
) : CalendarSlotBindingRepository {

    private object Keys {
        val PENDING_NAME = stringPreferencesKey("pending_calendar_name")
        val PENDING_FINGERPRINT = stringPreferencesKey("pending_fingerprint")
        val PENDING_TIME = longPreferencesKey("pending_timestamp")
        val PENDING_EXISTING_IDS = stringPreferencesKey("pending_existing_ids")

        fun calendarKey(calendarId: Long) = stringPreferencesKey("calendar_id_$calendarId")
    }

    private suspend fun getPreferences(): Preferences {
        return context.slotBindingDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .first()
    }

    override fun calculateFingerprint(timeSlots: List<TimeSlot>): String {
        if (timeSlots.isEmpty()) return ""
        val sortedSlots = timeSlots.sortedBy { it.number }
        val raw = sortedSlots.joinToString(";") { "${it.number}:${it.startTime}-${it.endTime}" }
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    override suspend fun getBinding(calendarId: Long): String? {
        val prefs = getPreferences()
        return prefs[Keys.calendarKey(calendarId)]
    }

    override suspend fun bind(calendarId: Long, calendarName: String, fingerprint: String) {
        if (calendarId <= 0 || fingerprint.isBlank()) return
        context.slotBindingDataStore.edit { preferences ->
            preferences[Keys.calendarKey(calendarId)] = fingerprint
        }
        Log.i(TAG, "已建立日历作息指纹绑定: ID=#$calendarId, 名称='$calendarName', 指纹=${fingerprint.take(8)}...")
    }

    override suspend fun unbind(calendarId: Long) {
        context.slotBindingDataStore.edit { preferences ->
            preferences.remove(Keys.calendarKey(calendarId))
        }
        Log.i(TAG, "已移除日历 #$calendarId 的本地作息指纹绑定")
    }

    override suspend fun isSlotMatching(calendarId: Long, timeSlots: List<TimeSlot>): Boolean {
        if (timeSlots.isEmpty()) return false
        val currentFingerprint = calculateFingerprint(timeSlots)
        val savedFingerprint = getBinding(calendarId)
        val matched = savedFingerprint != null && savedFingerprint == currentFingerprint
        Log.i(TAG, "日历 #$calendarId 作息指纹比对: 匹配=$matched, 本地记录=${savedFingerprint?.take(8) ?: "无"}, 待导入=${currentFingerprint.take(8)}")
        return matched
    }

    override suspend fun setPendingExport(
        existingCalendarIds: Set<Long>,
        targetName: String,
        fingerprint: String
    ) {
        if (fingerprint.isBlank()) return
        context.slotBindingDataStore.edit { preferences ->
            preferences[Keys.PENDING_NAME] = targetName
            preferences[Keys.PENDING_FINGERPRINT] = fingerprint
            preferences[Keys.PENDING_TIME] = System.currentTimeMillis()
            preferences[Keys.PENDING_EXISTING_IDS] = existingCalendarIds.joinToString(",")
        }
        Log.i(TAG, "记录待绑定导出状态: 预期课表名称='$targetName', 现有日历IDs=$existingCalendarIds, 指纹=${fingerprint.take(8)}...")
    }

    override suspend fun checkPendingExportResult(currentCalendars: List<VivoCourseCalendar>): PendingExportResult {
        val prefs = getPreferences()
        val time = prefs[Keys.PENDING_TIME] ?: return PendingExportResult.NoPending
        // 超过 10 分钟则超时失效，自动清理
        if (System.currentTimeMillis() - time >= 10 * 60 * 1000L) {
            clearPendingBinding()
            return PendingExportResult.NoPending
        }

        val pendingName = prefs[Keys.PENDING_NAME] ?: ""
        val pendingFingerprint = prefs[Keys.PENDING_FINGERPRINT] ?: ""
        val existingIdsStr = prefs[Keys.PENDING_EXISTING_IDS] ?: ""
        val existingIds = existingIdsStr.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()

        // 查找新增的日历（ID 不在唤起前的快照中）
        val newCalendar = currentCalendars.firstOrNull { it.id !in existingIds }
            ?: (if (pendingName.isNotBlank()) currentCalendars.find { it.displayName == pendingName } else null)

        if (newCalendar != null) {
            if (pendingFingerprint.isNotBlank()) {
                bind(newCalendar.id, newCalendar.displayName, pendingFingerprint)
            }
            clearPendingBinding()
            Log.i(TAG, "检测到课程表导入保存成功: ID=#${newCalendar.id}, 名称='${newCalendar.displayName}'")
            return PendingExportResult.Success(newCalendar)
        } else {
            // 没有检测到新生成的日历，判定用户已取消或放弃
            clearPendingBinding()
            Log.i(TAG, "未检测到新增日历，判定用户已取消或放弃导入保存")
            return PendingExportResult.Cancelled
        }
    }

    override suspend fun clearPendingBinding() {
        context.slotBindingDataStore.edit { preferences ->
            preferences.remove(Keys.PENDING_NAME)
            preferences.remove(Keys.PENDING_FINGERPRINT)
            preferences.remove(Keys.PENDING_TIME)
            preferences.remove(Keys.PENDING_EXISTING_IDS)
        }
    }

    companion object {
        private const val TAG = "VivoSync_SlotBind"
    }
}
