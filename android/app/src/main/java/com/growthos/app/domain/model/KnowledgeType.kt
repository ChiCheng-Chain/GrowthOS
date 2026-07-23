package com.growthos.app.domain.model

/**
 * 知识类型。经验(知道的事)/ 待办(该做的事)。
 * 同 [Attribution] / [TrainingStatus],Room 以 [name] 入库,[label] 仅 UI 展示。
 */
enum class KnowledgeType(val label: String) {
    EXPERIENCE("经验"),
    TODO("待办")
}
