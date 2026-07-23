package com.growthos.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.growthos.app.domain.model.KnowledgeType
import kotlinx.serialization.Serializable

/**
 * 知识(Knowledge)。从视频、经验贴等外部渠道摄取的认知,按领域组织。
 *
 * 与 [Principle] 区分:Principle 是自己犯错实践沉淀的可迁移认知;
 * Knowledge 是外部摄取的。两者独立,各管各的。
 *
 * - [type]:经验(陈述性,知道的事)/ 待办(规范性,该做的事)。
 * - [done]:待办完成标记(经验类型始终 false,UI 忽略)。
 *
 * [domainId] 软关联(无外键),对齐 Principle 范式:关联领域被隐藏/删除时知识保留。
 */
@Serializable
@Entity(
    tableName = "knowledges",
    indices = [Index(value = ["domainId"])]
)
data class Knowledge(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val type: KnowledgeType,
    val createdAt: Long,
    val domainId: Long? = null,
    val done: Boolean = false
)
