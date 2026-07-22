package com.growthos.app.util

import java.util.Calendar
import java.util.TimeZone

/**
 * 时间工具:统一 epoch millis 与"今日 / 最近 N 天"窗口计算。
 * 数据层只存 Long(epoch millis),UI 层格式化另处理。所有窗口基于本地时区。
 */
object TimeUtil {

    fun nowMillis(): Long = System.currentTimeMillis()

    /** 今天 0:00(本地时区)的 epoch millis,"今日样本"窗口下界。 */
    fun startOfTodayMillis(timeZone: TimeZone = TimeZone.getDefault()): Long =
        startOfDayMillis(nowMillis(), timeZone)

    /** 明天 0:00(本地时区),今日窗口上界(开区间)。 */
    fun startOfNextDayMillis(timeZone: TimeZone = TimeZone.getDefault()): Long =
        startOfTodayMillis(timeZone) + DAY_MILLIS

    /** 任意时间戳所在天的 0:00(本地时区)。 */
    fun startOfDayMillis(epochMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = epochMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * 最近 N 天窗口:[startOfToday − (N−1) 天, startOfNextDay)。
     * N=7 即"最近 7 天"含今天(R-009)。
     */
    fun lastNDaysRange(n: Int, timeZone: TimeZone = TimeZone.getDefault()): LongRange {
        val todayStart = startOfTodayMillis(timeZone)
        val start = todayStart - (n - 1) * DAY_MILLIS
        val endExclusive = todayStart + DAY_MILLIS
        return start until endExclusive
    }

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
}
