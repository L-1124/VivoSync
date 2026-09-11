package com.app.vivosync.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.LocalTime

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDate {
        return LocalDate.parse(decoder.decodeString())
    }
}

/**
 * 单节课作息时间定义
 */
@Serializable
data class TimeSlot(
    val number: Int,             // 节次序号：1, 2, 3...
    val startTime: String,       // 格式 "08:00"
    val endTime: String          // 格式 "08:45"
) {
    /**
     * 获取开始时间距离当天 00:00 的绝对分钟数（vivo 协议核心参数 beginTime）
     */
    fun getStartMinuteOfDay(): Int {
        return runCatching {
            val t = LocalTime.parse(startTime)
            (t.hour * 60) + t.minute
        }.getOrElse {
            val parts = startTime.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            (h * 60) + m
        }
    }

    /**
     * 获取结束时间距离当天 00:00 的绝对分钟数（vivo 协议核心参数 endTime）
     */
    fun getEndMinuteOfDay(): Int {
        return runCatching {
            val t = LocalTime.parse(endTime)
            t.hour * 60 + t.minute
        }.getOrElse {
            val parts = endTime.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            h * 60 + m
        }
    }

    /**
     * 获取 vivo 协议所要求的时段类型：
     * 1 = 上午 (TIME_SLOT_MORNING)
     * 2 = 下午 (TIME_SLOT_AFTERNOON)
     * 3 = 晚上 (TIME_SLOT_EVENING)
     */
    fun getTimeSlotType(): Int {
        val startMin = getStartMinuteOfDay()
        return when {
            startMin < 12 * 60 -> 1 // 12:00 前为上午
            startMin < 18 * 60 -> 2 // 18:00 前为下午
            else -> 3               // 18:00 后为晚上
        }
    }
}

/**
 * 课程实体（包含周次、节次、教室、教师和 vivo 专有原子颜色）
 */
@Serializable
data class CourseItem(
    val name: String,
    val teacher: String = "",
    val room: String = "",
    val dayOfWeek: Int,          // 1=周一 .. 7=周日
    val startSection: Int,       // 开始节次 (如 1)
    val endSection: Int,         // 结束节次 (如 2)
    val weeks: List<Int>,        // 上课周次列表，如 [1, 2, 3, 4, 5, 6, 7, 8]
    val vivoColorIndex: Int = 1  // vivo 课程表 12 种原子配色索引 (1~12)
)

/**
 * vivo OriginOS 原生课程表 12 色原子色彩枚举
 */
enum class VivoCourseColor(val index: Int, val displayName: String, val hexColor: String) {
    MINT_GREEN(1, "薄荷绿", "#66BB6A"),
    CORAL_ORANGE(2, "珊瑚橙", "#FFA726"),
    OCEAN_BLUE(3, "海浪蓝", "#42A5F5"),
    ROSE_PURPLE(4, "蔷薇紫", "#AB47BC"),
    LEMON_YELLOW(5, "柠檬黄", "#FFEE58"),
    TEAL(6, "青竹绿", "#26A69A"),
    SAKURA_PINK(7, "樱花粉", "#EC407A"),
    SKY_CYAN(8, "天青蓝", "#29B6F6"),
    LAVENDER(9, "薰衣紫", "#7E57C2"),
    LIGHT_ORANGE(10, "暖日橙", "#FF7043"),
    LIGHT_BROWN(11, "奶咖棕", "#8D6E63"),
    COOL_GREY(12, "冷月灰", "#78909C");

    companion object {
        fun fromIndex(index: Int): VivoCourseColor {
            return entries.find { it.index == index } ?: MINT_GREEN
        }
    }
}

/**
 * 课表同步数据载荷
 */
@Serializable
data class SyncPayload(
    @Serializable(with = LocalDateSerializer::class)
    val semesterStartDate: LocalDate = LocalDate.now(), // 学期开学日期，默认当前
    val semesterTotalWeeks: Int = 20,                   // 学期总周数
    val timeSlots: List<TimeSlot> = emptyList(),        // 各节次作息时间表
    val courses: List<CourseItem>,                      // 课程列表
    val alarmMinutes: Int = 15,                         // 课前提前提醒闹钟（分钟），默认提前 15 分钟
    val skippedDates: Set<String> = emptySet()          // 调休/节假日跳过日期集合，格式 YYYY-MM-DD
)

// vivo 官方课程表 JSON 通信协议 DTO

@Serializable
data class VivoCourseProtocolDto(
    val result: Int = 0,
    val message: String = "success",
    val courseName: String? = null,
    val tableName: String? = null,
    val timeSetting: VivoTimeSettingDto,
    val classes: List<VivoCourseClassDto>
)

@Serializable
data class VivoTimeSettingDto(
    val duration: Int,
    val breaks: Int = 10,
    val times: List<VivoTimeSlotDto>
)

@Serializable
data class VivoTimeSlotDto(
    val timeSlot: Int,
    val selection: Int,
    val beginTime: Int,
    val endTime: Int
)

@Serializable
data class VivoCourseClassDto(
    val className: String,
    val teacher: String = "",
    val room: String = "",
    val dayOfWeek: Int,
    val start: Int,
    val end: Int,
    val weeks: List<Int>,
    val singleOrBiWeekly: Int = 0
)
