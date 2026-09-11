package com.app.vivosync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.core.graphics.toColorInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.app.vivosync.R
import com.app.vivosync.model.CourseItem
import com.app.vivosync.model.VivoCourseCalendar
import com.app.vivosync.model.VivoCourseColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursePreviewSyncDialog(
    courses: List<CourseItem>,
    startDate: java.time.LocalDate? = null,
    totalWeeks: Int? = null,
    vivoCalendars: List<VivoCourseCalendar>,
    isSyncing: Boolean,
    onDismissRequest: () -> Unit,
    onConfirmSync: (targetCalendarId: Long) -> Unit,
) {
    // 默认选中当前激活的日历，若无则默认新建 (-1L)
    var selectedCalId by remember {
        val active = vivoCalendars.firstOrNull { it.isActive }
        mutableLongStateOf(active?.id ?: if (vivoCalendars.isNotEmpty()) vivoCalendars.first().id else -1L)
    }

    val isNewCalendar = selectedCalId <= 0

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.preview_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(R.string.preview_dialog_subtitle, courses.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )

                // 开学日期提示
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (startDate != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        val dateText = if (startDate != null) {
                            val aligned = com.app.vivosync.engine.VivoDateAligner.getAlignedSemesterStart(startDate)
                            stringResource(R.string.semester_start_info, aligned.toString())
                        } else {
                            stringResource(R.string.semester_start_fallback)
                        }
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (startDate != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                        if ((totalWeeks != null) && (totalWeeks > 0)) {
                            Text(
                                text = stringResource(R.string.semester_total_weeks, totalWeeks),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 目标日历选择区域
                Text(
                    text = stringResource(R.string.target_vivo_calendar),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    // 选项 1: 新建 vivo 课程表
                    item(key = -1L) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCalId = -1L }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCalId == -1L,
                                onClick = { selectedCalId = -1L }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.new_vivo_calendar_option),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.new_calendar_hint_system),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 选项 2..N: 已有课表列表
                    items(vivoCalendars, key = { it.id }) { cal ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCalId = cal.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCalId == cal.id,
                                onClick = { selectedCalId = cal.id }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = cal.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (cal.isActive) {
                                        Text(
                                            text = stringResource(R.string.calendar_active_tag),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.calendar_existing_schedule_desc, cal.courseCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 课程清单展示
                Text(
                    text = stringResource(R.string.course_list_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(courses) { course ->
                        CoursePreviewItemRow(course = course)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        enabled = !isSyncing
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val buttonText = when {
                        isSyncing && isNewCalendar -> stringResource(R.string.action_invoking)
                        isSyncing -> stringResource(R.string.action_syncing)
                        isNewCalendar -> stringResource(R.string.action_invoke_system_import)
                        else -> stringResource(R.string.action_confirm_sync)
                    }

                    Button(
                        onClick = {
                            onConfirmSync(selectedCalId)
                        },
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(buttonText)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoursePreviewItemRow(course: CourseItem) {
    val dayNames = arrayOf(
        stringResource(R.string.day_monday),
        stringResource(R.string.day_tuesday),
        stringResource(R.string.day_wednesday),
        stringResource(R.string.day_thursday),
        stringResource(R.string.day_friday),
        stringResource(R.string.day_saturday),
        stringResource(R.string.day_sunday)
    )
    val dayText = dayNames.getOrElse(course.dayOfWeek - 1) { stringResource(R.string.day_format, course.dayOfWeek) }
    val colorDef = VivoCourseColor.fromIndex(course.vivoColorIndex)
    val circleColor = runCatching { Color(colorDef.hexColor.toColorInt()) }.getOrDefault(MaterialTheme.colorScheme.primary)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(circleColor, CircleShape)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val sectionRangeText = stringResource(R.string.course_section_range, course.startSection, course.endSection)
                val details = buildString {
                    append(dayText)
                    append(sectionRangeText)
                    if (course.room.isNotBlank()) {
                        append(" · ${course.room}")
                    }
                    if (course.teacher.isNotBlank()) {
                        append(" · ${course.teacher}")
                    }
                }

                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
