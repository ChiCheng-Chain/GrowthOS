package com.growthos.app.util

import java.util.Calendar
import java.util.TimeZone

/**
 * 练习时长折算与文案(feature 2026-09-17 / 设计 D7 折算侧)。
 * 录入表达层 = 日期 + 0~23 小时 + 时长(分钟);存库 = startAt/endAt epoch millis(BR-4)。
 */
object DurationFormat {

    const val MAX_DURATION_MINUTES = 24L * 60

    /**
     * 折算:某日 [dayEpochMillis](该日任意时刻均可,取其日 0 点)+ 小时起点 + 时长(分钟)。
     * 返回 (startedAt, endedAt);秒位抹零(BR-8)。
     */
    fun toEpochRange(
        dayMillis: Long,
        hourOfDay: Int,
        durationMinutes: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Pair<Long, Long> {
        require(hourOfDay in 0..23) { "hourOfDay out of range: $hourOfDay" }
        require(durationMinutes in 1..MAX_DURATION_MINUTES) {
            "durationMinutes out of range: $durationMinutes"
        }
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = TimeUtil.startOfDayMillis(dayMillis, timeZone)
        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
        val start = cal.timeInMillis
        return start to start + durationMinutes * MINUTE_MILLIS
    }

    /** 时长毫秒 → 文案(BR-9):「45 分」「1 小时 23 分」;跨天封顶显示 24 小时。 */
    fun formatMillis(millis: Long): String {
        val totalMinutes = millis / MINUTE_MILLIS
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours <= 0 -> "${minutes}分"
            minutes == 0L -> "${hours}小时"
            else -> "${hours}小时${minutes}分"
        }
    }

    /** 时长分钟 → 文案(输入侧预览)。 */
    fun formatMinutes(minutes: Long): String = formatMillis(minutes * MINUTE_MILLIS)

    /** 分钟 ⇄ 小时单位折算(输入切换):小时值×60。 */
    fun hoursToMinutes(hours: Long): Long = hours * 60

    fun minutesToHours(minutes: Long): Long = minutes / 60

    private const val MINUTE_MILLIS = 60L * 1000
}
