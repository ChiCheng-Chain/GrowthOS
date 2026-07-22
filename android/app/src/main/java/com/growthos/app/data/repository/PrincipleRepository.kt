package com.growthos.app.data.repository

import com.growthos.app.data.local.dao.PrincipleDao
import com.growthos.app.data.local.entity.Principle
import com.growthos.app.data.local.relation.PrincipleWithNames
import com.growthos.app.util.TimeUtil
import kotlinx.coroutines.flow.Flow

class PrincipleRepository(private val dao: PrincipleDao) {

    suspend fun create(content: String, domainId: Long? = null, errorTypeId: Long? = null,
                       trainingId: Long? = null, sampleId: Long? = null): Long =
        dao.insert(
            Principle(
                content = content,
                createdAt = TimeUtil.nowMillis(),
                domainId = domainId,
                errorTypeId = errorTypeId,
                trainingId = trainingId,
                sampleId = sampleId
            )
        )

    /** 直接插入已构造好的 Principle(供 VM 控制 createdAt 等字段,对齐 SampleRepository.insert 范式)。 */
    suspend fun insert(principle: Principle): Long = dao.insert(principle)

    suspend fun update(principle: Principle) = dao.update(principle)

    suspend fun delete(principle: Principle) = dao.delete(principle)

    fun observeAll(): Flow<List<Principle>> = dao.observeAll()

    fun observeByDomain(domainId: Long): Flow<List<Principle>> = dao.observeByDomain(domainId)

    /** 阶段 6 原则列表页:原则 + 关联领域名 + 错误类型名(LEFT JOIN 容错)。 */
    fun observeAllWithNames(): Flow<List<PrincipleWithNames>> = dao.observeAllWithNames()

    suspend fun getById(id: Long): Principle? = dao.getById(id)
}
