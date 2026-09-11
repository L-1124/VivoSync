package com.app.vivosync.guard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * vivo 设备与系统日历课程表组件兼容性状态
 */
sealed interface VivoSupportStatus {
    data object Supported : VivoSupportStatus
    data class NotVivoDevice(val manufacturer: String, val brand: String) : VivoSupportStatus
    data object CalendarNotInstalled : VivoSupportStatus
    data object CourseFeatureNotSupported : VivoSupportStatus
}

class VivoEnvironmentGuard(private val context: Context) {

    companion object {
        const val VIVO_CALENDAR_PACKAGE = "com.bbk.calendar"
        const val COURSE_PREVIEW_ACTION = "vivo.intent.action.calendar.COURSE_PREVIEW"
        const val COURSE_OVERVIEW_ACTION = "vivo.intent.action.calendar.COURSE_OVERVIEW"
        const val COURSE_ACCOUNT_NAME = "Course account"
        const val COURSE_ACCOUNT_TYPE = "LOCAL"
    }

    /**
     * 运行完整的 vivo / iQOO 设备与课程表支持度校验
     */
    fun checkCompatibility(): VivoSupportStatus {
        // 1. 厂商与品牌检测
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val isVivoOrIqoo = manufacturer.contains("vivo") || manufacturer.contains("bbk") ||
                brand.contains("vivo") || brand.contains("iqoo")

        if (!isVivoOrIqoo) {
            return VivoSupportStatus.NotVivoDevice(Build.MANUFACTURER, Build.BRAND)
        }

        // 2. 检查 vivo 系统日历应用是否存在
        val pm = context.packageManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(
                    VIVO_CALENDAR_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(VIVO_CALENDAR_PACKAGE, PackageManager.GET_META_DATA)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return VivoSupportStatus.CalendarNotInstalled
        }

        // 3. 检查是否支持课程表协议 Action: vivo.intent.action.calendar.COURSE_PREVIEW
        val previewIntent = Intent(COURSE_PREVIEW_ACTION).setPackage(VIVO_CALENDAR_PACKAGE)
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(
                previewIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(previewIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }

        if (resolveInfo == null) {
            // 如果预览 Action 解析失败，尝试检查 COURSE_OVERVIEW 作为第二特征
            val overviewIntent = Intent(COURSE_OVERVIEW_ACTION).setPackage(VIVO_CALENDAR_PACKAGE)
            val overviewResolve = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveActivity(
                    overviewIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.resolveActivity(overviewIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            if (overviewResolve == null) {
                return VivoSupportStatus.CourseFeatureNotSupported
            }
        }

        return VivoSupportStatus.Supported
    }
}
