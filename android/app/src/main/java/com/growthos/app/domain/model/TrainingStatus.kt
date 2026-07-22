package com.growthos.app.domain.model

/**
 * 训练项状态机(R-010):进行中 / 已完成 / 已放弃。同样以 [name] 入库。
 */
enum class TrainingStatus(val label: String) {
    IN_PROGRESS("进行中"),
    COMPLETED("已完成"),
    ABANDONED("已放弃")
}
