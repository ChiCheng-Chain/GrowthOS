package com.growthos.app.data.local.relation

import androidx.room.Embedded
import com.growthos.app.data.local.entity.Sample

/**
 * 带错误类型名与领域名的样本投影(R-007 列表展示用)。
 * 用 @Query JOIN 一次取全,避免 @Relation 的逐行额外查询。
 */
data class SampleWithErrorType(
    @Embedded val sample: Sample,
    val errorTypeName: String,
    val domainName: String
)
