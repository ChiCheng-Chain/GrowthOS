package com.growthos.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.growthos.app.data.local.entity.ErrorType
import kotlinx.coroutines.flow.Flow

@Dao
interface ErrorTypeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(errorType: ErrorType): Long

    @Query("SELECT * FROM error_types ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ErrorType>>

    @Query("SELECT * FROM error_types WHERE id = :id")
    suspend fun getById(id: Long): ErrorType?

    @Query("SELECT * FROM error_types WHERE name = :name")
    suspend fun getByName(name: String): ErrorType?

    /** 被样本引用时阻止删除(R-014):返回引用计数,>0 则提示无法删除。 */
    @Query("SELECT COUNT(*) FROM samples WHERE errorTypeId = :id")
    suspend fun sampleReferenceCount(id: Long): Int

    /** 被训练项引用时阻止删除(R-014)。 */
    @Query("SELECT COUNT(*) FROM trainings WHERE errorTypeId = :id")
    suspend fun trainingReferenceCount(id: Long): Int

    @Query("DELETE FROM error_types WHERE id = :id")
    suspend fun delete(id: Long)
}
