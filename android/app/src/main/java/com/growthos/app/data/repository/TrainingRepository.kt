package com.growthos.app.data.repository

import com.growthos.app.data.local.dao.TrainingDao
import com.growthos.app.data.local.entity.Training
import com.growthos.app.data.local.relation.TrainingEffectStats
import com.growthos.app.data.local.relation.TrainingWithNames
import com.growthos.app.data.local.relation.TrainingWithTypeName
import com.growthos.app.domain.model.TrainingStatus
import com.growthos.app.util.TimeUtil
import kotlinx.coroutines.flow.Flow

class TrainingRepository(private val dao: TrainingDao) {

    suspend fun create(training: Training): Long = dao.insert(training)

    suspend fun update(training: Training) = dao.update(training)

    suspend fun delete(training: Training) = dao.delete(training)

    suspend fun getById(id: Long): Training? = dao.getById(id)

    fun observeInProgress(): Flow<List<Training>> = dao.observeInProgress()

    fun observeByDomain(domainId: Long): Flow<List<Training>> = dao.observeByDomain(domainId)

    fun observeByErrorType(errorTypeId: Long): Flow<List<Training>> = dao.observeByErrorType(errorTypeId)

    /** 领域页 F3(阶段 3 D3):该领域进行中训练项 + 关联错误类型名。 */
    fun observeInProgressByDomainWithTypeName(domainId: Long): Flow<List<TrainingWithTypeName>> =
        dao.observeInProgressByDomainWithTypeName(domainId)

    /** 阶段 5 训练项列表页:全部训练项 + 错误类型名 + 领域名(按状态+时间排序)。 */
    fun observeAllWithNames(): Flow<List<TrainingWithNames>> = dao.observeAllWithNames()

    /** R-010 结束训练项:置状态 + 记录结束时间。 */
    suspend fun finish(id: Long, status: TrainingStatus) {
        val t = dao.getById(id) ?: return
        dao.update(
            t.copy(
                status = status,
                endedAt = TimeUtil.nowMillis()
            )
        )
    }

    /** R-011 训练前后对比。 */
    suspend fun effectStats(errorTypeId: Long, startedAt: Long): TrainingEffectStats =
        dao.effectStats(errorTypeId, startedAt)
}
