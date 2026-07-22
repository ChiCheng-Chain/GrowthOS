package com.growthos.app.data.local.relation

import androidx.room.Embedded
import com.growthos.app.data.local.entity.Principle

/**
 * 原则 + 关联领域名 + 错误类型名(阶段 6 列表页展示用)。
 * 用 @Query LEFT JOIN 一次取全——软关联可 null,故 LEFT JOIN,关联对象被删时名为 null,
 * UI 层容错(对齐 Principle 实体"无外键约束,关联对象删时原则保留"语义,设计 D5)。
 * trainingId/sampleId 关联太稀疏,列表页不展示,故不 JOIN(设计 D6)。
 */
data class PrincipleWithNames(
    @Embedded val principle: Principle,
    val domainName: String?,
    val errorTypeName: String?
)
