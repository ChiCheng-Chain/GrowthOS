package com.growthos.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.growthos.app.data.local.entity.PracticeSession
import com.growthos.app.data.local.relation.PracticeSessionWithDomainName
import kotlinx.coroutines.flow.Flow

/**
 * 练习记录 DAO(feature 2026-09-17)。
 * 统计 SQL 全部 domainId=0 全局哨兵 + 开区间 [startMillis, endMillis),对齐 SampleDao 惯例。
 * 进行中不变式(endedAt IS NULL 至多一条)由 Repository 层维护(设计 D3)。
 */
@Dao
interface PracticeSessionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: PracticeSession): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(sessions: List<PracticeSession>)

    @Update
    suspend fun update(session: PracticeSession)

    @Query("DELETE FROM practice_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM practice_sessions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM practice_sessions")
    suspend fun countAll(): Int

    @Query("SELECT * FROM practice_sessions WHERE id = :id")
    suspend fun getById(id: Long): PracticeSession?

    /** 进行中的一条(至多一条,D3 不变式)。 */
    @Query("SELECT * FROM practice_sessions WHERE endedAt IS NULL LIMIT 1")
    suspend fun getActive(): PracticeSession?

    @Query("SELECT * FROM practice_sessions WHERE endedAt IS NULL LIMIT 1")
    fun observeActive(): Flow<PracticeSession?>

    @Query("SELECT * FROM practice_sessions ORDER BY startedAt DESC")
    suspend fun getAll(): List<PracticeSession>

    /** 重叠判定(设计 D6):endAt 为空行与 [start,end) 相交即重叠;excludeId 供编辑场景排除自身。 */
    @Query(
        """
        SELECT COUNT(*) FROM practice_sessions
        WHERE endedAt IS NULL
          AND startedAt < :endMillis
          AND id != :excludeId
        """
    )
    suspend fun countActiveOverlapping(endMillis: Long, excludeId: Long): Int

    /** 列表展示:带领域名,LEFT JOIN 容错(对齐 PrincipleWithNames 范式,设计 D12)。 */
    @Query(
        """
        SELECT p.*, d.name AS domainName FROM practice_sessions p
        LEFT JOIN domains d ON p.domainId = d.id
        ORDER BY p.startedAt DESC, p.id DESC
        """
    )
    fun observeAllWithDomain(): Flow<List<PracticeSessionWithDomainName>>

    /** 看板①:窗口内总时长(毫秒)与完成条数。进行中行不计入(BR-2:时长由起止差推导)。 */
    @Query(
        """
        SELECT COALESCE(SUM(endedAt - startedAt), 0) AS totalMillis, COUNT(*) AS count
        FROM practice_sessions
        WHERE endedAt IS NOT NULL
          AND (:domainId = 0 OR domainId = :domainId)
          AND startedAt >= :startMillis AND startedAt < :endMillis
        """
    )
    fun observeTotalAndCount(
        domainId: Long,
        startMillis: Long,
        endMillis: Long
    ): Flow<PracticeTotalAndCount>

    /** 看板②:按领域分组的窗口内时长(JOIN domains 取名,hidden 照常计入——对齐样本统计现状)。 */
    @Query(
        """
        SELECT p.domainId AS domainId, d.name AS domainName,
               COALESCE(SUM(p.endedAt - p.startedAt), 0) AS totalMillis
        FROM practice_sessions p
        LEFT JOIN domains d ON p.domainId = d.id
        WHERE p.endedAt IS NOT NULL
          AND (:domainId = 0 OR p.domainId = :domainId)
          AND p.startedAt >= :startMillis AND p.startedAt < :endMillis
        GROUP BY p.domainId
        ORDER BY totalMillis DESC
        """
    )
    fun observeByDomain(
        domainId: Long,
        startMillis: Long,
        endMillis: Long
    ): Flow<List<PracticeDomainTotal>>

    /** 看板③:窗口内明细行,供 ViewModel 按本地时区分桶(设计 D9/D10,SQL 无时区日界函数)。 */
    @Query(
        """
        SELECT startedAt, endedAt, domainId FROM practice_sessions
        WHERE endedAt IS NOT NULL
          AND (:domainId = 0 OR domainId = :domainId)
          AND startedAt >= :startMillis AND startedAt < :endMillis
        """
    )
    fun observeWindowDetails(
        domainId: Long,
        startMillis: Long,
        endMillis: Long
    ): Flow<List<PracticeDetail>>
}

/** 看板① 投影:总时长 + 完成条数。 */
data class PracticeTotalAndCount(
    val totalMillis: Long,
    val count: Int
)

/** 看板② 投影:领域时长合计。 */
data class PracticeDomainTotal(
    val domainId: Long,
    val domainName: String?,
    val totalMillis: Long
)

/** 看板③ 投影:明细行(分桶输入)。 */
data class PracticeDetail(
    val startedAt: Long,
    val endedAt: Long,
    val domainId: Long
)
