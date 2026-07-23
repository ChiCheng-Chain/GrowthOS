package com.growthos.app.data.repository

import com.growthos.app.data.local.dao.KnowledgeDao
import com.growthos.app.data.local.entity.Knowledge
import com.growthos.app.data.local.relation.KnowledgeWithDomainName
import com.growthos.app.domain.model.KnowledgeType
import com.growthos.app.util.TimeUtil
import kotlinx.coroutines.flow.Flow

/**
 * 知识仓库。对齐 [PrincipleRepository] 范式:纯委托 DAO,具体类(不抽 interface)。
 */
class KnowledgeRepository(private val dao: KnowledgeDao) {

    suspend fun create(content: String, type: KnowledgeType, domainId: Long? = null): Long =
        dao.insert(
            Knowledge(
                content = content,
                type = type,
                createdAt = TimeUtil.nowMillis(),
                domainId = domainId
            )
        )

    /** 直接插入已构造好的 Knowledge(供 VM 控制 createdAt 等字段)。 */
    suspend fun insert(knowledge: Knowledge): Long = dao.insert(knowledge)

    suspend fun update(knowledge: Knowledge) = dao.update(knowledge)

    suspend fun delete(knowledge: Knowledge) = dao.delete(knowledge)

    /** 切换待办完成状态(经验类型 done 无意义,但仍可调,UI 不暴露)。 */
    suspend fun setDone(id: Long, done: Boolean) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(done = done))
    }

    fun observeAll(): Flow<List<Knowledge>> = dao.observeAll()

    fun observeByDomain(domainId: Long): Flow<List<Knowledge>> = dao.observeByDomain(domainId)

    fun observeAllWithDomainName(): Flow<List<KnowledgeWithDomainName>> = dao.observeAllWithDomainName()

    suspend fun getById(id: Long): Knowledge? = dao.getById(id)
}
