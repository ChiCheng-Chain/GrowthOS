package com.growthos.app.data.local

import com.growthos.app.domain.model.Polarity

/**
 * R-004 首次启动种子关键因素。跨领域复用,作为引导,避免用户从零创建。
 * 在 Database 首次创建时写入(见 [GrowthOSDatabase] 的 onCreate 回调)。
 *
 * 按极性分组(feature 2026-09-16 / 设计 D3):负向=错误引导,正向=成功因素引导。
 * Migration(3,4) 只补种 positiveNames——存量库的负向词条已在库中。
 */
object ErrorTypeSeed {
    val negativeNames = listOf(
        "边界条件遗漏",
        "信息不足就行动",
        "对局面判断错误",
        "贪收益导致下限崩盘",
        "执行变形",
        "压力下急躁",
        "复查不足",
        "忽略反馈信号"
    )

    val positiveNames = listOf(
        "执行到位",
        "状态良好",
        "判断准确",
        "运气不错"
    )

    /** 全部种子(name → polarity),供 SeedCallback 遍历。 */
    val all: List<Pair<String, Polarity>> =
        negativeNames.map { it to Polarity.NEGATIVE } + positiveNames.map { it to Polarity.POSITIVE }
}
