package com.growthos.app.data.repository

import com.growthos.app.data.local.dao.PracticeDetail
import com.growthos.app.data.local.dao.PracticeDomainTotal
import com.growthos.app.data.local.dao.PracticeSessionDao
import com.growthos.app.data.local.dao.PracticeTotalAndCount
import com.growthos.app.data.local.entity.PracticeSession
import com.growthos.app.data.local.relation.PracticeSessionWithDomainName
import com.growthos.app.util.TimeUtil
import kotlinx.coroutines.flow.Flow

/**
 * 练习记录仓储(feature 2026-09-17)。
 * 进行中不变式(endedAt IS NULL 至多一条)的唯一维护点(D3):
 * start 拒绝第二条;save 拦与进行中重叠的历史时段(D6);导入路径由 DataImporter 显式修复。
 */
class PracticeSessionRepository(private val dao: PracticeSessionDao) {

    suspend fun getById(id: Long): PracticeSession? = dao.getById(id)

    suspend fun getActive(): PracticeSession? = dao.getActive()

    fun observeActive(): Flow<PracticeSession?> = dao.observeActive()

    fun observeAllWithDomain(): Flow<List<PracticeSessionWithDomainName>> =
        dao.observeAllWithDomain()

    /**
     * 开始计时:插入进行中行。已存在进行中则拒绝(返回 null,调用方引导结束上一条,AC-03)。
     */
    suspend fun start(domainId: Long, now: Long): Long? {
        if (dao.getActive() != null) return null
        return dao.insert(PracticeSession(domainId = domainId, startedAt = now, endedAt = null, createdAt = now))
    }

    /** 结束计时:补 endAt 与备注。id 不存在时静默忽略(理论不可达)。 */
    suspend fun finish(id: Long, endedAt: Long, note: String?) {
        val session = dao.getById(id) ?: return
        dao.update(session.copy(endedAt = endedAt, note = note))
    }

    /**
     * 补录/编辑保存:与进行中行时间区间重叠则拒绝(BR-7/D6,返回 false)。
     * [startedAt]/[endedAt] 由 DurationFormat 折算;历史记录之间重叠不拦。
     */
    suspend fun save(
        id: Long?,
        domainId: Long,
        startedAt: Long,
        endedAt: Long,
        note: String?,
        createdAt: Long = TimeUtil.nowMillis()
    ): Boolean {
        if (dao.countActiveOverlapping(endedAt, excludeId = id ?: -1L) > 0) return false
        if (id == null) {
            dao.insert(PracticeSession(domainId = domainId, startedAt = startedAt, endedAt = endedAt, note = note, createdAt = createdAt))
        } else {
            val existing = dao.getById(id) ?: return false
            dao.update(existing.copy(domainId = domainId, startedAt = startedAt, endedAt = endedAt, note = note))
        }
        return true
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun getAll(): List<PracticeSession> = dao.getAll()

    /** 用于导入还原:保持文件主键,绕过 start 的不变式(导入器自行处理多条 active,设计 D13)。 */
    suspend fun insertAllForImport(sessions: List<PracticeSession>) {
        sessions.forEach { dao.insert(it) }
    }

    // ---------- 统计(看板,设计 D9) ----------

    fun observeTotalAndCount(
        domainId: Long,
        startMillis: Long,
        endMillis: Long
    ): Flow<PracticeTotalAndCount> = dao.observeTotalAndCount(domainId, startMillis, endMillis)

    fun observeByDomain(
        domainId: Long,
        startMillis: Long,
        endMillis: Long
    ): Flow<List<PracticeDomainTotal>> = dao.observeByDomain(domainId, startMillis, endMillis)

    fun observeWindowDetails(
        domainId: Long,
        startMillis: Long,
        endMillis: Long
    ): Flow<List<PracticeDetail>> = dao.observeWindowDetails(domainId, startMillis, endMillis)

    /** 便捷重载:最近 N 天(对齐 SampleRepository.LastNDays 惯例)。 */
    fun observeTotalAndCountLastNDays(n: Int, domainId: Long = 0): Flow<PracticeTotalAndCount> {
        val range = TimeUtil.lastNDaysRange(n)
        return observeTotalAndCount(domainId, range.first, range.last)
    }
}
