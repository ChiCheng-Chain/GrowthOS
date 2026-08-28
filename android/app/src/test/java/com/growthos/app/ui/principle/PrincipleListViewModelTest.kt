package com.growthos.app.ui.principle

import com.growthos.app.data.local.dao.DomainDao
import com.growthos.app.data.local.dao.ErrorTypeDao
import com.growthos.app.data.local.dao.PrincipleDao
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.data.local.entity.Principle
import com.growthos.app.data.local.relation.PrincipleWithNames
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.ErrorTypeRepository
import com.growthos.app.data.repository.PrincipleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PrincipleListViewModel] 状态逻辑单测(二期阶段 6 / 设计 §测试)。
 *
 * 套路同 TrainingListViewModelTest:内存假 DAO 喂真 Repository,StandardTestDispatcher
 * 控时序,不引 truth/turbine(踩坑 P10)。共享 Fake 供同包 PrincipleEditViewModelTest 复用。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PrincipleListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm(dao: FakePrincipleDao): PrincipleListViewModel =
        PrincipleListViewModel(PrincipleRepository(dao), DomainRepository(FakeDomainDao()))

    @Test
    fun `empty list yields empty state`() = runTest(testDispatcher) {
        val vm = newVm(FakePrincipleDao())
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isEmpty)
    }

    @Test
    fun `domain filter narrows list and null restores all`() = runTest(testDispatcher) {
        val dao = FakePrincipleDao()
        dao.insert(Principle(id = 1, content = "编程领域", createdAt = 100L, domainId = 1L))
        dao.insert(Principle(id = 2, content = "战旗领域", createdAt = 200L, domainId = 2L))
        val vm = newVm(dao)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.filteredPrinciples.size)

        vm.filterByDomain(1L)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.filteredPrinciples.size)
        assertEquals(1L, vm.uiState.value.filteredPrinciples.first().principle.domainId)

        vm.filterByDomain(null)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.filteredPrinciples.size)
    }

    @Test
    fun `principles ordered by createdAt desc`() = runTest(testDispatcher) {
        val dao = FakePrincipleDao()
        dao.insert(Principle(id = 1, content = "早", createdAt = 100L))
        dao.insert(Principle(id = 2, content = "晚", createdAt = 300L))
        dao.insert(Principle(id = 3, content = "中", createdAt = 200L))

        val vm = newVm(dao)
        advanceUntilIdle()
        val list = vm.uiState.value.principles
        assertEquals(3, list.size)
        assertEquals(listOf(2L, 3L, 1L), list.map { it.principle.id })
    }

    @Test
    fun `join resolves domain and error type names`() = runTest(testDispatcher) {
        val dao = FakePrincipleDao()
        dao.seedDomain(1, "编程")
        dao.seedErrorType(10, "边界条件遗漏")
        dao.insert(Principle(id = 1, content = "原则", createdAt = 0, domainId = 1, errorTypeId = 10))

        val vm = newVm(dao)
        advanceUntilIdle()
        val row = vm.uiState.value.principles.first()
        assertEquals("编程", row.domainName)
        assertEquals("边界条件遗漏", row.errorTypeName)
    }

    @Test
    fun `soft relation null yields null names`() = runTest(testDispatcher) {
        val dao = FakePrincipleDao()
        // 关联的领域/错误类型不存在(或为 null)→ 名为 null(D5 容错)
        dao.insert(Principle(id = 1, content = "无关联原则", createdAt = 0))

        val vm = newVm(dao)
        advanceUntilIdle()
        val row = vm.uiState.value.principles.first()
        assertNull(row.domainName)
        assertNull(row.errorTypeName)
    }

    @Test
    fun `delete removes principle and refreshes list`() = runTest(testDispatcher) {
        val dao = FakePrincipleDao()
        dao.insert(Principle(id = 1, content = "原则一", createdAt = 0))
        dao.insert(Principle(id = 2, content = "原则二", createdAt = 1))
        val vm = newVm(dao)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.principles.size)

        vm.delete(dao.getById(1)!!)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.principles.size)
        assertEquals(2L, vm.uiState.value.principles.first().principle.id)
    }
}

// ---------- 共享 Fake(供 PrincipleEditViewModelTest 复用) ----------

internal const val pDomain = 1L
internal const val pErrorType = 10L

internal fun makePrinciple(
    id: Long = 0,
    content: String = "原则",
    createdAt: Long = 0L,
    domainId: Long? = null,
    errorTypeId: Long? = null,
    trainingId: Long? = null,
    sampleId: Long? = null
) = Principle(
    id = id, content = content, createdAt = createdAt,
    domainId = domainId, errorTypeId = errorTypeId,
    trainingId = trainingId, sampleId = sampleId
)

/**
 * 内存假 PrincipleDao:实现 observeAllWithNames(LEFT JOIN 容错)+ 全套 CRUD。
 * seedDomain/seedErrorType 注入关联名映射,供 JOIN 解析。
 */
internal class FakePrincipleDao : PrincipleDao {
    private val all = MutableStateFlow<List<Principle>>(emptyList())
    private val domains = mutableMapOf<Long, String>()
    private val errorTypes = mutableMapOf<Long, String>()
    private var counter = 0L

    fun seedDomain(id: Long, name: String) { domains[id] = name }
    fun seedErrorType(id: Long, name: String) { errorTypes[id] = name }

    // 导入 feature 扩展,Fake 不涉及,空实现。
    override suspend fun insertAll(principles: List<Principle>) {}

    override suspend fun deleteAll() {}

    override suspend fun countAll(): Int = 0

    override suspend fun insert(principle: Principle): Long {
        val id = if (principle.id == 0L) { counter += 1; counter } else principle.id
        all.update { it + principle.copy(id = id) }
        return id
    }

    override suspend fun update(principle: Principle) {
        all.update { list -> list.map { if (it.id == principle.id) principle else it } }
    }

    override suspend fun delete(principle: Principle) {
        all.update { list -> list.filterNot { it.id == principle.id } }
    }

    override fun observeAll(): Flow<List<Principle>> =
        all.map { it.sortedByDescending { p -> p.createdAt } }

    override fun observeByDomain(domainId: Long): Flow<List<Principle>> =
        all.map { it.filter { p -> p.domainId == domainId }.sortedByDescending { p -> p.createdAt } }

    override suspend fun getById(id: Long): Principle? = all.value.firstOrNull { it.id == id }

    override fun observeAllWithNames(): Flow<List<PrincipleWithNames>> =
        all.map { list ->
            list.sortedByDescending { it.createdAt }
                .map { p ->
                    PrincipleWithNames(
                        principle = p,
                        domainName = p.domainId?.let { domains[it] },
                        errorTypeName = p.errorTypeId?.let { errorTypes[it] }
                    )
                }
        }
}

/** 内存假 DomainDao:实现 observeVisible。 */
internal class FakeDomainDao : DomainDao {
    private val all = MutableStateFlow<List<Domain>>(emptyList())

    fun seed(id: Long, name: String) {
        all.update { it + Domain(id = id, name = name, createdAt = 0, hidden = false) }
    }

    // 导入 feature 扩展,Fake 不涉及,空实现。
    override suspend fun insertAll(domains: List<Domain>) {}

    override suspend fun deleteAll() {}

    override suspend fun countAll(): Int = 0

    override suspend fun insert(domain: Domain): Long {
        all.update { it + domain }
        return domain.id
    }

    override suspend fun update(domain: Domain) {
        all.update { list -> list.map { if (it.id == domain.id) domain else it } }
    }

    override fun observeAll(): Flow<List<Domain>> = all.asStateFlow()
    override fun observeVisible(): Flow<List<Domain>> = all.map { it.filter { d -> !d.hidden } }
    override suspend fun getById(id: Long): Domain? = all.value.firstOrNull { it.id == id }
    override fun observeById(id: Long): Flow<Domain?> = all.map { it.firstOrNull { d -> d.id == id } }
    override suspend fun setHidden(id: Long, hidden: Boolean) {
        all.update { list -> list.map { if (it.id == id) it.copy(hidden = hidden) else it } }
    }
}

/** 内存假 ErrorTypeDao:实现 observeAll + getById。 */
internal class FakeErrorTypeDao : ErrorTypeDao {
    private val all = MutableStateFlow<List<ErrorType>>(emptyList())

    fun seed(id: Long, name: String) {
        all.update { it + ErrorType(id = id, name = name, createdAt = 0) }
    }

    // 导入 feature 扩展,Fake 不涉及,空实现。
    override suspend fun insertAll(errorTypes: List<ErrorType>) {}

    override suspend fun deleteAll() {}

    override suspend fun countAll(): Int = 0

    override suspend fun insert(errorType: ErrorType): Long {
        all.update { it + errorType }
        return errorType.id
    }

    override fun observeAll(): Flow<List<ErrorType>> = all.asStateFlow()
    override suspend fun getById(id: Long): ErrorType? = all.value.firstOrNull { it.id == id }
    override suspend fun getByName(name: String): ErrorType? = all.value.firstOrNull { it.name == name }
    override suspend fun update(errorType: ErrorType) {
        all.update { list -> list.map { if (it.id == errorType.id) errorType else it } }
    }
    override suspend fun sampleReferenceCount(id: Long): Int = 0
    override suspend fun trainingReferenceCount(id: Long): Int = 0
    override suspend fun reassignSamples(fromId: Long, toId: Long) {}
    override suspend fun reassignTrainings(fromId: Long, toId: Long) {}
    override suspend fun delete(id: Long) {
        all.update { list -> list.filterNot { it.id == id } }
    }
}
