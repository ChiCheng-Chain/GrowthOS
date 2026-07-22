package com.growthos.app.data.local

/**
 * R-004 首次启动种子错误类型。跨领域复用,作为引导,避免用户从零创建。
 * 在 Database 首次创建时写入(见 [GrowthOSDatabase] 的 onCreate 回调)。
 */
object ErrorTypeSeed {
    val names = listOf(
        "边界条件遗漏",
        "信息不足就行动",
        "对局面判断错误",
        "贪收益导致下限崩盘",
        "执行变形",
        "压力下急躁",
        "复查不足",
        "忽略反馈信号"
    )
}
