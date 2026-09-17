package com.growthos.app.domain.model

/**
 * 样本归因类型(R-005)。固定枚举,用 Room TypeConverter 以 [name] 入库,
 * 不在数据层建独立表。[label] 仅用于 UI 展示。
 *
 * 六值(feature 2026-09-16 / 设计 D5):原四值 + 两个正向值,
 * 成功样本照常必填归因(BR-4)。只可在末尾追加,不可改名/删值(存量按 name 存取)。
 */
enum class Attribution(val label: String) {
    CONTROLLABLE("可控错误"),
    UNCONTROLLABLE("不可控波动"),
    OPPONENT_EXTERNAL("对手或外部强"),
    ENVIRONMENT("环境问题"),
    CONTROLLABLE_STRENGTH("可控优势"),
    OPPONENT_WEAK("对手或外部弱")
}
