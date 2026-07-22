package com.growthos.app.data.repository

import com.growthos.app.data.local.dao.DomainDao
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.util.TimeUtil
import kotlinx.coroutines.flow.Flow

class DomainRepository(private val dao: DomainDao) {
    suspend fun create(name: String): Long =
        dao.insert(Domain(name = name, createdAt = TimeUtil.nowMillis()))

    suspend fun rename(id: Long, name: String) {
        dao.getById(id)?.let { dao.update(it.copy(name = name)) }
    }

    suspend fun setHidden(id: Long, hidden: Boolean) = dao.setHidden(id, hidden)

    fun observeAll(): Flow<List<Domain>> = dao.observeAll()
    fun observeVisible(): Flow<List<Domain>> = dao.observeVisible()
    fun observeById(id: Long): Flow<Domain?> = dao.observeById(id)
    suspend fun getById(id: Long): Domain? = dao.getById(id)
}
