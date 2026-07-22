package com.growthos.app.data.local.relation

import androidx.room.Embedded
import com.growthos.app.data.local.entity.Training

/**
 * 训练项 + 关联错误类型名 + 领域名(阶段 5 列表页展示用)。
 * 用 @Query 双 JOIN 一次取全,避免逐条 getById 的 N+1。
 * 与 [TrainingWithTypeName] 区别:那个只带错误类型名、按单领域且只进行中;
 * 本类带领域名、全量状态,供训练项列表页使用。
 */
data class TrainingWithNames(
    @Embedded val training: Training,
    val errorTypeName: String,
    val domainName: String
)
