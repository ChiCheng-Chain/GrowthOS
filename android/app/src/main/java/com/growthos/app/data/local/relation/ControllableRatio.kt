package com.growthos.app.data.local.relation

/** 可控错误占比(R-009):分母 [total],分子 [controllable]。 */
data class ControllableRatio(
    val total: Int,
    val controllable: Int
)
