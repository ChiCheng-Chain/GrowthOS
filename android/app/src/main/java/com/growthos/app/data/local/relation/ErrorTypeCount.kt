package com.growthos.app.data.local.relation

/** 高频错误统计行(R-009 高频前三):错误类型 + 出现次数,按次数倒序。 */
data class ErrorTypeCount(
    val errorTypeId: Long,
    val errorTypeName: String,
    val count: Int
)
