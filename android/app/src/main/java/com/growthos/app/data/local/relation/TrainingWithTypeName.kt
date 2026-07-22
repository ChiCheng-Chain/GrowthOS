package com.growthos.app.data.local.relation

import androidx.room.Embedded
import com.growthos.app.data.local.entity.Training

/**
 * 训练项 + 关联错误类型名(领域页 F3 展示用,阶段 3 D3)。
 * 用 @Query JOIN 一次取全,避免逐条 getById 的 N+1。对齐 SampleWithErrorType 模式。
 */
data class TrainingWithTypeName(
    @Embedded val training: Training,
    val errorTypeName: String
)
