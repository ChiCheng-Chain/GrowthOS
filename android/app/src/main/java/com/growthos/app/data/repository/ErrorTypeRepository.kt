package com.growthos.app.data.repository

import com.growthos.app.data.local.dao.ErrorTypeDao
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.util.TimeUtil
import kotlinx.coroutines.flow.Flow

/**
 * 错误类型仓库契约(R-004 / R-014 / CRUD 补全)。
 *
 * 抽成 interface 便于测试注入桩实现([com.growthos.app.ui.error_type.ErrorTypeListViewModelTest]
 * 验状态机不依赖 Room 异步 Flow)。默认实现 [ErrorTypeRepositoryImpl] 封装撞名合并逻辑。
 */
interface ErrorTypeRepository {
    fun observeAll(): Flow<List<ErrorType>>

    suspend fun getById(id: Long): ErrorType?

    /** 新增,重名复用(GET 语义)。 */
    suspend fun getOrCreate(name: String): Long

    /** 改名,撞名走合并(迁移引用 + 删旧 id)。 */
    suspend fun rename(id: Long, name: String)

    /** 删除前引用检查(样本 + 训练项)。 */
    suspend fun referenceCount(id: Long): Int

    suspend fun delete(id: Long)
}

/**
 * 默认实现:撞名合并时迁移 samples/trainings 引用到同名项,再删旧 id。
 * 外键未配 CASCADE(阶段 0),UPDATE 不触发级联,安全。
 */
class ErrorTypeRepositoryImpl(private val dao: ErrorTypeDao) : ErrorTypeRepository {

    override fun observeAll(): Flow<List<ErrorType>> = dao.observeAll()

    override suspend fun getById(id: Long): ErrorType? = dao.getById(id)

    override suspend fun getOrCreate(name: String): Long {
        dao.getByName(name)?.let { return it.id }
        return dao.insert(ErrorType(name = name, createdAt = TimeUtil.nowMillis()))
    }

    override suspend fun rename(id: Long, name: String) {
        val target = dao.getById(id) ?: return
        val trimmed = name.trim()
        if (target.name == trimmed) return
        val existing = dao.getByName(trimmed)
        if (existing == null) {
            dao.update(target.copy(name = trimmed))
        } else {
            dao.reassignSamples(id, existing.id)
            dao.reassignTrainings(id, existing.id)
            dao.delete(id)
        }
    }

    override suspend fun referenceCount(id: Long): Int =
        dao.sampleReferenceCount(id) + dao.trainingReferenceCount(id)

    override suspend fun delete(id: Long) = dao.delete(id)
}
