package com.growthos.app.domain.model

/**
 * 样本归因类型(R-005)。固定四值枚举,用 Room TypeConverter 以 [name] 入库,
 * 不在数据层建独立表。[label] 仅用于 UI 展示。
 */
enum class Attribution(val label: String) {
    CONTROLLABLE("可控错误"),
    UNCONTROLLABLE("不可控波动"),
    OPPONENT_EXTERNAL("对手或外部强"),
    ENVIRONMENT("环境问题")
}
