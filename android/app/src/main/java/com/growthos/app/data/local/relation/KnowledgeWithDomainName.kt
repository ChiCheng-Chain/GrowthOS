package com.growthos.app.data.local.relation

import androidx.room.Embedded
import com.growthos.app.data.local.entity.Knowledge

/**
 * 知识 + 关联领域名(知识库列表页展示用)。
 * LEFT JOIN,软关联可 null,关联领域被隐藏/删除时领域名为 null,UI 容错。
 * 对齐 [PrincipleWithNames] 范式,但只 JOIN domains(知识不关联错误类型)。
 */
data class KnowledgeWithDomainName(
    @Embedded val knowledge: Knowledge,
    val domainName: String?
)
