package com.growthos.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.growthos.app.data.local.entity.Sample
import com.growthos.app.data.local.relation.ControllableRatio
import com.growthos.app.data.local.relation.ErrorTypeCount
import com.growthos.app.data.local.relation.SampleWithErrorType
import kotlinx.coroutines.flow.Flow

@Dao
interface SampleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(sample: Sample): Long

    /** 导入用:批量插入,保持文件中的主键 id(2026-08-27 导入 feature)。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(samples: List<Sample>)

    /** 导入用:当前库计数(parse 时双向对照数据)。 */
    @Query("SELECT COUNT(*) FROM samples")
    suspend fun countAll(): Int

    /** 导入用:清库重建(与 insertAll 同事务,见 DataImporterImpl)。 */
    @Query("DELETE FROM samples")
    suspend fun deleteAll()

    @Update
    suspend fun update(sample: Sample)

    @Delete
    suspend fun delete(sample: Sample)

    @Query("SELECT * FROM samples WHERE id = :id")
    suspend fun getById(id: Long): Sample?

    @Query("SELECT * FROM samples WHERE id = :id")
    fun observeById(id: Long): Flow<Sample?>

    /** 今日样本(开区间 [startOfToday, startOfNextDay))。 */
    @Query(
        """
        SELECT * FROM samples
        WHERE recordedAt >= :startOfToday AND recordedAt < :startOfNextDay
        ORDER BY recordedAt DESC
        """
    )
    fun observeToday(startOfToday: Long, startOfNextDay: Long): Flow<List<Sample>>

    @Query("SELECT * FROM samples ORDER BY recordedAt DESC")
    fun observeAll(): Flow<List<Sample>>

    /** R-007 按领域查看(含倒序)。 */
    @Query("SELECT * FROM samples WHERE domainId = :domainId ORDER BY recordedAt DESC")
    fun observeByDomain(domainId: Long): Flow<List<Sample>>

    @Query("SELECT * FROM samples WHERE errorTypeId = :errorTypeId ORDER BY recordedAt DESC")
    fun observeByErrorType(errorTypeId: Long): Flow<List<Sample>>

    @Query("SELECT * FROM samples WHERE attribution = :attribution ORDER BY recordedAt DESC")
    fun observeByAttribution(attribution: String): Flow<List<Sample>>

    /**
     * R-007 列表展示用:带错误类型名与领域名的样本,一次 JOIN 取全。
     * 时间范围可选,传 0 表示不限(全部时间)。
     */
    @Query(
        """
        SELECT s.*, et.name AS errorTypeName, d.name AS domainName
        FROM samples s
        INNER JOIN error_types et ON s.errorTypeId = et.id
        INNER JOIN domains d ON s.domainId = d.id
        WHERE (:domainId = 0 OR s.domainId = :domainId)
          AND (:startMillis = 0 OR s.recordedAt >= :startMillis)
          AND (:endMillis = 0 OR s.recordedAt < :endMillis)
        ORDER BY s.recordedAt DESC
        """
    )
    fun observeWithNames(
        domainId: Long = 0,
        startMillis: Long = 0,
        endMillis: Long = 0
    ): Flow<List<SampleWithErrorType>>

    /** 领域页最近样本。 */
    @Query(
        """
        SELECT s.*, et.name AS errorTypeName, d.name AS domainName
        FROM samples s
        INNER JOIN error_types et ON s.errorTypeId = et.id
        INNER JOIN domains d ON s.domainId = d.id
        WHERE s.domainId = :domainId
        ORDER BY s.recordedAt DESC
        LIMIT :limit
        """
    )
    fun observeRecentByDomain(domainId: Long, limit: Int): Flow<List<SampleWithErrorType>>

    /**
     * §6 高频错误前三(R-009)。
     * domainId=0 表示跨领域全局统计;时间范围 [startMillis, endMillis) 开区间。
     */
    @Query(
        """
        SELECT s.errorTypeId AS errorTypeId, et.name AS errorTypeName, COUNT(*) AS count
        FROM samples s
        INNER JOIN error_types et ON s.errorTypeId = et.id
        WHERE (:domainId = 0 OR s.domainId = :domainId)
          AND s.recordedAt >= :startMillis AND s.recordedAt < :endMillis
        GROUP BY s.errorTypeId
        ORDER BY count DESC
        LIMIT :limit
        """
    )
    fun observeTopErrorTypes(
        domainId: Long,
        startMillis: Long,
        endMillis: Long,
        limit: Int
    ): Flow<List<ErrorTypeCount>>

    /**
     * §6 可控错误占比(R-009)。分母 [total],分子 [controllable]。
     * 单条 SQL 出两个值,避免两次查询。情绪强度为空的样本仍计入 total。
     */
    @Query(
        """
        SELECT
          COUNT(*) AS total,
          SUM(CASE WHEN attribution = 'CONTROLLABLE' THEN 1 ELSE 0 END) AS controllable
        FROM samples
        WHERE (:domainId = 0 OR domainId = :domainId)
          AND recordedAt >= :startMillis AND recordedAt < :endMillis
        """
    )
    fun observeControllableRatio(
        domainId: Long,
        startMillis: Long,
        endMillis: Long
    ): Flow<ControllableRatio?>

    /**
     * §6 情绪强度最高的样本(R-009)。只返回有情绪强度的样本;
     * 窗口内全 null 时查不到行,Flow emit null。
     */
    @Query(
        """
        SELECT s.*, et.name AS errorTypeName, d.name AS domainName
        FROM samples s
        INNER JOIN error_types et ON s.errorTypeId = et.id
        INNER JOIN domains d ON s.domainId = d.id
        WHERE (:domainId = 0 OR s.domainId = :domainId)
          AND s.recordedAt >= :startMillis AND s.recordedAt < :endMillis
          AND s.emotionIntensity IS NOT NULL
        ORDER BY s.emotionIntensity DESC
        LIMIT 1
        """
    )
    fun observeHighestEmotion(
        domainId: Long,
        startMillis: Long,
        endMillis: Long
    ): Flow<SampleWithErrorType?>

    /** R-011 训练后相关样本列表(recordedAt >= startedAt)。 */
    @Query(
        """
        SELECT s.*, et.name AS errorTypeName, d.name AS domainName
        FROM samples s
        INNER JOIN error_types et ON s.errorTypeId = et.id
        INNER JOIN domains d ON s.domainId = d.id
        WHERE s.errorTypeId = :errorTypeId AND s.recordedAt >= :startedAt
        ORDER BY s.recordedAt DESC
        """
    )
    fun observeSamplesAfter(errorTypeId: Long, startedAt: Long): Flow<List<SampleWithErrorType>>

    /** 删除前引用检查用:领域下样本数(R-014)。 */
    @Query("SELECT COUNT(*) FROM samples WHERE domainId = :domainId")
    suspend fun countByDomain(domainId: Long): Int

    /**
     * §6 周复盘 F1:时间窗口内样本数(跨或单领域)。
     * domainId=0 表示全局。返回 Flow,随录入变化自动推送(设计 D1)。
     */
    @Query(
        """
        SELECT COUNT(*) FROM samples
        WHERE (:domainId = 0 OR domainId = :domainId)
          AND recordedAt >= :startMillis AND recordedAt < :endMillis
        """
    )
    fun observeCount(
        domainId: Long,
        startMillis: Long,
        endMillis: Long
    ): Flow<Int>

    /**
     * §6 周复盘 F5:可控归因样本中频次最高的单个错误类型(高频 + 可控交叉)。
     * WHERE attribution='CONTROLLABLE' 一次出结果,不复用 F2 过滤(避免 N+1,设计 D2)。
     * 无可控错误时查不到行,Flow emit null。
     */
    @Query(
        """
        SELECT s.errorTypeId AS errorTypeId, et.name AS errorTypeName, COUNT(*) AS count
        FROM samples s
        INNER JOIN error_types et ON s.errorTypeId = et.id
        WHERE (:domainId = 0 OR s.domainId = :domainId)
          AND s.recordedAt >= :startMillis AND s.recordedAt < :endMillis
          AND s.attribution = 'CONTROLLABLE'
        GROUP BY s.errorTypeId
        ORDER BY count DESC
        LIMIT 1
        """
    )
    fun observeTopControllableErrorType(
        domainId: Long,
        startMillis: Long,
        endMillis: Long
    ): Flow<ErrorTypeCount?>
}
