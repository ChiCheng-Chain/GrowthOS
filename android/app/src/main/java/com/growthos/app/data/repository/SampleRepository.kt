package com.growthos.app.data.repository

import com.growthos.app.data.local.dao.SampleDao
import com.growthos.app.data.local.entity.Sample
import com.growthos.app.data.local.relation.ControllableRatio
import com.growthos.app.data.local.relation.ErrorTypeCount
import com.growthos.app.data.local.relation.SampleWithErrorType
import com.growthos.app.domain.model.Attribution
import com.growthos.app.util.TimeUtil
import kotlinx.coroutines.flow.Flow

/**
 * 样本仓储(技术方案 §6)。所有统计走 SQL 聚合,不在内存算。
 * UI 只接触本类,不直接碰 DAO。
 */
class SampleRepository(private val dao: SampleDao) {

    suspend fun insert(sample: Sample): Long = dao.insert(sample)

    suspend fun update(sample: Sample) = dao.update(sample)

    suspend fun delete(sample: Sample) = dao.delete(sample)

    suspend fun getById(id: Long): Sample? = dao.getById(id)

    fun observeById(id: Long): Flow<Sample?> = dao.observeById(id)

    /** 今日样本(R-002 列表)。窗口基于本地时区,每次 collect 时实时算下界。 */
    fun observeToday(): Flow<List<Sample>> =
        dao.observeToday(TimeUtil.startOfTodayMillis(), TimeUtil.startOfNextDayMillis())

    fun observeAll(): Flow<List<Sample>> = dao.observeAll()

    fun observeByDomain(domainId: Long): Flow<List<Sample>> = dao.observeByDomain(domainId)

    fun observeByErrorType(errorTypeId: Long): Flow<List<Sample>> = dao.observeByErrorType(errorTypeId)

    fun observeByAttribution(attribution: Attribution): Flow<List<Sample>> =
        dao.observeByAttribution(attribution.name)

    /** R-007 列表展示用:带错误类型名与领域名。 */
    fun observeWithNames(
        domainId: Long = 0,
        startMillis: Long = 0,
        endMillis: Long = 0
    ): Flow<List<SampleWithErrorType>> = dao.observeWithNames(domainId, startMillis, endMillis)

    fun observeRecentByDomain(domainId: Long, limit: Int): Flow<List<SampleWithErrorType>> =
        dao.observeRecentByDomain(domainId, limit)

    /** §6 高频错误前三。domainId=0 表示全局。 */
    fun observeTopErrorTypes(
        domainId: Long,
        startMillis: Long,
        endMillis: Long,
        limit: Int = 3
    ): Flow<List<ErrorTypeCount>> =
        dao.observeTopErrorTypes(domainId, startMillis, endMillis, limit)

    /** §6 可控错误占比。 */
    fun observeControllableRatio(
        domainId: Long,
        startMillis: Long,
        endMillis: Long
    ): Flow<ControllableRatio?> = dao.observeControllableRatio(domainId, startMillis, endMillis)

    /** §6 情绪强度最高的样本。 */
    fun observeHighestEmotion(
        domainId: Long,
        startMillis: Long,
        endMillis: Long
    ): Flow<SampleWithErrorType?> = dao.observeHighestEmotion(domainId, startMillis, endMillis)

    /** R-011 训练后相关样本列表。 */
    fun observeSamplesAfter(errorTypeId: Long, startedAt: Long): Flow<List<SampleWithErrorType>> =
        dao.observeSamplesAfter(errorTypeId, startedAt)

    /** R-014:领域下样本数。 */
    suspend fun countByDomain(domainId: Long): Int = dao.countByDomain(domainId)

    /** 便捷重载:最近 N 天的高频错误(默认 7 天,对齐 R-009)。 */
    fun observeTopErrorTypesLastNDays(
        n: Int,
        domainId: Long = 0,
        limit: Int = 3
    ): Flow<List<ErrorTypeCount>> {
        val range = TimeUtil.lastNDaysRange(n)
        return dao.observeTopErrorTypes(domainId, range.first, range.last, limit)
    }

    fun observeControllableRatioLastNDays(
        n: Int,
        domainId: Long = 0
    ): Flow<ControllableRatio?> {
        val range = TimeUtil.lastNDaysRange(n)
        return dao.observeControllableRatio(domainId, range.first, range.last)
    }

    fun observeHighestEmotionLastNDays(
        n: Int,
        domainId: Long = 0
    ): Flow<SampleWithErrorType?> {
        val range = TimeUtil.lastNDaysRange(n)
        return dao.observeHighestEmotion(domainId, range.first, range.last)
    }

    /** 周复盘 F1:最近 N 天样本数(跨或单领域),随录入自动刷新(设计 D1)。 */
    fun observeCountLastNDays(
        n: Int,
        domainId: Long = 0
    ): Flow<Int> {
        val range = TimeUtil.lastNDaysRange(n)
        return dao.observeCount(domainId, range.first, range.last)
    }

    /** 周复盘 F5:最近 N 天可控归因样本中频次最高的错误类型(高频 + 可控交叉,设计 D2)。 */
    fun observeTopControllableErrorTypeLastNDays(
        n: Int,
        domainId: Long = 0
    ): Flow<ErrorTypeCount?> {
        val range = TimeUtil.lastNDaysRange(n)
        return dao.observeTopControllableErrorType(domainId, range.first, range.last)
    }
}
