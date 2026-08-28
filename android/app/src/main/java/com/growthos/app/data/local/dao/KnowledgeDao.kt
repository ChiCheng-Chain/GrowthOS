package com.growthos.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.growthos.app.data.local.entity.Knowledge
import com.growthos.app.data.local.relation.KnowledgeWithDomainName
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(knowledge: Knowledge): Long

    /** 导入用:批量插入,保持文件中的主键 id(2026-08-27 导入 feature)。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(knowledges: List<Knowledge>)

    /** 导入用:当前库计数(parse 时双向对照数据)。 */
    @Query("SELECT COUNT(*) FROM knowledges")
    suspend fun countAll(): Int

    /** 导入用:清库重建(与 insertAll 同事务,见 DataImporterImpl)。 */
    @Query("DELETE FROM knowledges")
    suspend fun deleteAll()

    @Update
    suspend fun update(knowledge: Knowledge)

    @Delete
    suspend fun delete(knowledge: Knowledge)

    @Query("SELECT * FROM knowledges ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Knowledge>>

    @Query("SELECT * FROM knowledges WHERE domainId = :domainId ORDER BY createdAt DESC")
    fun observeByDomain(domainId: Long): Flow<List<Knowledge>>

    @Query("SELECT * FROM knowledges WHERE id = :id")
    suspend fun getById(id: Long): Knowledge?

    /**
     * 知识库列表页:知识 + 关联领域名。LEFT JOIN(软关联可 null)。
     * 按 createdAt 倒序。只 JOIN domains(比 Principle 少一张 error_types)。
     */
    @Query(
        """
        SELECT k.*, d.name AS domainName
        FROM knowledges k
        LEFT JOIN domains d ON k.domainId = d.id
        ORDER BY k.createdAt DESC
        """
    )
    fun observeAllWithDomainName(): Flow<List<KnowledgeWithDomainName>>
}
