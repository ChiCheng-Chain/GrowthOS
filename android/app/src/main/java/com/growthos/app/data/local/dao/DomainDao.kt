package com.growthos.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.growthos.app.data.local.entity.Domain
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(domain: Domain): Long

    /** 导入用:批量插入,保持文件中的主键 id(2026-08-27 导入 feature)。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(domains: List<Domain>)

    /** 导入用:当前库计数(parse 时双向对照数据)。 */
    @Query("SELECT COUNT(*) FROM domains")
    suspend fun countAll(): Int

    /** 导入用:清库重建(与 insertAll 同事务,见 DataImporterImpl)。 */
    @Query("DELETE FROM domains")
    suspend fun deleteAll()

    @Update
    suspend fun update(domain: Domain)

    @Query("SELECT * FROM domains ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<Domain>>

    /** R-001:未隐藏的领域,用于样本录入选择。 */
    @Query("SELECT * FROM domains WHERE hidden = 0 ORDER BY createdAt ASC")
    fun observeVisible(): Flow<List<Domain>>

    @Query("SELECT * FROM domains WHERE id = :id")
    suspend fun getById(id: Long): Domain?

    @Query("SELECT * FROM domains WHERE id = :id")
    fun observeById(id: Long): Flow<Domain?>

    @Query("UPDATE domains SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)
}
