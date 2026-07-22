package com.growthos.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.growthos.app.data.local.entity.Principle
import com.growthos.app.data.local.relation.PrincipleWithNames
import kotlinx.coroutines.flow.Flow

@Dao
interface PrincipleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(principle: Principle): Long

    @Update
    suspend fun update(principle: Principle)

    @Delete
    suspend fun delete(principle: Principle)

    @Query("SELECT * FROM principles ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Principle>>

    @Query("SELECT * FROM principles WHERE domainId = :domainId ORDER BY createdAt DESC")
    fun observeByDomain(domainId: Long): Flow<List<Principle>>

    @Query("SELECT * FROM principles WHERE id = :id")
    suspend fun getById(id: Long): Principle?

    /**
     * 阶段 6 原则列表页:原则 + 关联领域名 + 错误类型名。
     * LEFT JOIN(软关联可 null,关联对象被删时名为 null)。按 createdAt 倒序。
     * trainingId/sampleId 不 JOIN(列表页不展示,设计 D6)。
     */
    @Query(
        """
        SELECT p.*, d.name AS domainName, et.name AS errorTypeName
        FROM principles p
        LEFT JOIN domains d ON p.domainId = d.id
        LEFT JOIN error_types et ON p.errorTypeId = et.id
        ORDER BY p.createdAt DESC
        """
    )
    fun observeAllWithNames(): Flow<List<PrincipleWithNames>>
}
