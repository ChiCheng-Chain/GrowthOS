package com.growthos.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.growthos.app.data.local.entity.Training
import com.growthos.app.data.local.relation.TrainingEffectStats
import com.growthos.app.data.local.relation.TrainingWithNames
import com.growthos.app.data.local.relation.TrainingWithTypeName
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(training: Training): Long

    /** 导入用:批量插入,保持文件中的主键 id(2026-08-27 导入 feature)。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(trainings: List<Training>)

    /** 导入用:当前库计数(parse 时双向对照数据)。 */
    @Query("SELECT COUNT(*) FROM trainings")
    suspend fun countAll(): Int

    /** 导入用:清库重建(与 insertAll 同事务,见 DataImporterImpl)。 */
    @Query("DELETE FROM trainings")
    suspend fun deleteAll()

    @Update
    suspend fun update(training: Training)

    @Delete
    suspend fun delete(training: Training)

    @Query("SELECT * FROM trainings WHERE id = :id")
    suspend fun getById(id: Long): Training?

    /** 进行中训练项(R-010)。 */
    @Query("SELECT * FROM trainings WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC")
    fun observeInProgress(): Flow<List<Training>>

    @Query("SELECT * FROM trainings WHERE domainId = :domainId ORDER BY startedAt DESC")
    fun observeByDomain(domainId: Long): Flow<List<Training>>

    @Query("SELECT * FROM trainings WHERE errorTypeId = :errorTypeId ORDER BY startedAt DESC")
    fun observeByErrorType(errorTypeId: Long): Flow<List<Training>>

    /**
     * 领域页 F3(阶段 3 D3):该领域进行中训练项 + 关联错误类型名,JOIN 一次取全。
     * 按 startedAt DESC。
     */
    @Query(
        """
        SELECT t.*, et.name AS errorTypeName
        FROM trainings t
        INNER JOIN error_types et ON t.errorTypeId = et.id
        WHERE t.domainId = :domainId AND t.status = 'IN_PROGRESS'
        ORDER BY t.startedAt DESC
        """
    )
    fun observeInProgressByDomainWithTypeName(domainId: Long): Flow<List<TrainingWithTypeName>>

    /**
     * R-011 训练前后对比:训练开始前/后该错误类型出现次数。
     * 前窗口:[0, startedAt),后窗口:[startedAt, now]。单次训练。
     */
    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM samples
             WHERE errorTypeId = :errorTypeId AND recordedAt < :startedAt) AS beforeCount,
          (SELECT COUNT(*) FROM samples
             WHERE errorTypeId = :errorTypeId AND recordedAt >= :startedAt) AS afterCount
        """
    )
    suspend fun effectStats(errorTypeId: Long, startedAt: Long): TrainingEffectStats

    /**
     * 阶段 5 训练项列表页:全部训练项 + 错误类型名 + 领域名,双 JOIN 一次取全。
     * 排序:进行中在前、已完成次之、已放弃最后;同状态按 startedAt 倒序。
     */
    @Query(
        """
        SELECT t.*, et.name AS errorTypeName, d.name AS domainName
        FROM trainings t
        INNER JOIN error_types et ON t.errorTypeId = et.id
        INNER JOIN domains d ON t.domainId = d.id
        ORDER BY
          CASE t.status WHEN 'IN_PROGRESS' THEN 0 WHEN 'COMPLETED' THEN 1 ELSE 2 END,
          t.startedAt DESC
        """
    )
    fun observeAllWithNames(): Flow<List<TrainingWithNames>>
}
