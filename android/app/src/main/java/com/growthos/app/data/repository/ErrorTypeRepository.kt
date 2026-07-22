package com.growthos.app.data.repository

import com.growthos.app.data.local.dao.ErrorTypeDao
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.util.TimeUtil
import kotlinx.coroutines.flow.Flow

class ErrorTypeRepository(private val dao: ErrorTypeDao) {

    fun observeAll(): Flow<List<ErrorType>> = dao.observeAll()

    suspend fun getById(id: Long): ErrorType? = dao.getById(id)

    /**
     * 新增错误类型(R-004)。重名时返回已有记录的 id(GET 语义),
     * 支持"新建并立即选中"——同名不报错,直接复用。
     */
    suspend fun getOrCreate(name: String): Long {
        dao.getByName(name)?.let { return it.id }
        return dao.insert(ErrorType(name = name, createdAt = TimeUtil.nowMillis()))
    }

    /** R-014:删除前检查引用。被引用则返回引用数,UI 提示"N 条样本在用"。 */
    suspend fun referenceCount(id: Long): Int =
        dao.sampleReferenceCount(id) + dao.trainingReferenceCount(id)

    suspend fun delete(id: Long) = dao.delete(id)
}
