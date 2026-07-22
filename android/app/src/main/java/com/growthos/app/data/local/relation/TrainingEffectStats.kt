package com.growthos.app.data.local.relation

/** 训练前后对比(R-011):训练开始前/后该错误类型出现次数。 */
data class TrainingEffectStats(
    val beforeCount: Int,
    val afterCount: Int
)
