package com.growthos.app.ui.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.growthos.app.ui.theme.MonoFamily
import com.growthos.app.util.DurationFormat
import com.growthos.app.util.TimeUtil
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 时长输入单位(Q6)。 */
enum class DurationUnit(val label: String) {
    MINUTES("分钟"),
    HOURS("小时")
}

/**
 * 时间编辑的核心状态(D7/BR-4):日期 + 0~23 小时 + 时长(分/时切换)→ startAt/endAt。
 * 补录、编辑、结束弹层「修改时间」三路共用一个模型。
 */
data class PracticeTimeInput(
    val dayStartMillis: Long,
    val hourOfDay: Int,
    val durationMinutes: Long,
    val unit: DurationUnit = DurationUnit.MINUTES
) {
    /** 输入框展示值:分钟单位显示分钟数;小时单位显示小时数(不足 1h 显示小数一位)。 */
    val displayValue: String
        get() = when (unit) {
            DurationUnit.MINUTES -> durationMinutes.toString()
            DurationUnit.HOURS -> {
                val whole = durationMinutes / 60
                val rem = durationMinutes % 60
                if (rem == 0L) whole.toString() else {
                    String.format(Locale.US, "%.1f", durationMinutes / 60.0)
                }
            }
        }

    /** 折算成 epoch 区间;非法输入返回 null。 */
    fun toEpochRange(): Pair<Long, Long>? = try {
        DurationFormat.toEpochRange(dayStartMillis, hourOfDay, durationMinutes)
    } catch (e: IllegalArgumentException) {
        null
    }
    companion object {
        /** 从既有记录还原(编辑态)。 */
        fun from(startedAt: Long, endedAt: Long): PracticeTimeInput {
            val cal = Calendar.getInstance()
            cal.timeInMillis = startedAt
            val minutes = ((endedAt - startedAt) / 60_000L).coerceAtLeast(1L)
            return PracticeTimeInput(
                dayStartMillis = TimeUtil.startOfDayMillis(startedAt),
                hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
                durationMinutes = minutes.coerceAtMost(DurationFormat.MAX_DURATION_MINUTES),
                unit = if (minutes >= 60) DurationUnit.HOURS else DurationUnit.MINUTES
            )
        }
    }
}

/**
 * 起点时间编辑器(设计 D7):日期选择行 + 0~23 小时 chips + 时长输入(单位切换)+
 * 推导结束时刻实时预览。不引入 M3 DatePicker 的完整弹窗——日期走「今天/昨天/前天/选日期」
 * 快捷行的简化方案在编辑页承载,本组件只负责小时+时长+预览,日期由调用方传值。
 */
@Composable
fun PracticeTimeEditor(
    input: PracticeTimeInput,
    onInputChanged: (PracticeTimeInput) -> Unit,
    modifier: Modifier = Modifier
) {
    val range = input.toEpochRange()
    Column(modifier = modifier.fillMaxWidth()) {
        // 0~23 小时 chips(D7:两行 0-11 / 12-23)
        Text(
            "开始于 ${formatDay(input.dayStartMillis)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        HourChipGrid(selectedHour = input.hourOfDay, onSelect = { hour ->
            onInputChanged(input.copy(hourOfDay = hour))
        })
        Spacer(Modifier.height(16.dp))

        // 时长输入:数字 + 单位切换(Q6)
        Text(
            "练习时长",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input.displayValue,
                onValueChange = { raw ->
                    val minutes = parseDuration(raw, input.unit)
                    if (minutes != null) {
                        onInputChanged(input.copy(durationMinutes = minutes))
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily)
            )
            Spacer(Modifier.width(12.dp))
            SingleChoiceSegmentedButtonRow {
                DurationUnit.entries.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = input.unit == unit,
                        onClick = { onInputChanged(input.copy(unit = unit)) },
                        shape = SegmentedButtonDefaults.itemShape(index, DurationUnit.entries.size)
                    ) { Text(unit.label) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // 推导预览(BR-4:结束时间自动算)
        val preview = if (range != null) {
            "${formatTime(range.first)} – ${formatTime(range.second)}"
        } else {
            "时长超出范围(1 分钟 – 24 小时)"
        }
        Text(
            preview,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = MonoFamily,
            color = if (range != null) MaterialTheme.colorScheme.onBackground
            else MaterialTheme.colorScheme.error
        )
    }
}

/** 0~23 小时选择网格(两行 0-11 / 12-23,账本 chips 视觉)。 */
@Composable
private fun HourChipGrid(selectedHour: Int, onSelect: (Int) -> Unit) {
    val hours = (0..23).toList()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        hours.chunked(8).forEach { rowHours ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowHours.forEach { hour ->
                    val selected = hour == selectedHour
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(hour) }
                    ) {
                        Text(
                            hour.toString().padStart(2, '0'),
                            modifier = Modifier.padding(vertical = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = MonoFamily,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 末行补齐空位,保持 chip 宽度一致
                repeat(8 - rowHours.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 输入解析:分钟单位直读;小时单位支持小数(0.5h=30 分)。非法/越界返回 null。 */
private fun parseDuration(raw: String, unit: DurationUnit): Long? {
    val trimmed = raw.trim().replace("　", " ")
    if (trimmed.isEmpty()) return null
    val value = trimmed.toBigDecimalOrNull() ?: return null
    val minutes = when (unit) {
        DurationUnit.MINUTES -> value.setScale(0, java.math.RoundingMode.FLOOR).toLong()
        DurationUnit.HOURS -> value.multiply(java.math.BigDecimal(60))
            .setScale(0, java.math.RoundingMode.FLOOR).toLong()
    }
    return if (minutes in 1..DurationFormat.MAX_DURATION_MINUTES) minutes else null
}

private fun formatDay(millis: Long): String =
    SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(millis))

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
