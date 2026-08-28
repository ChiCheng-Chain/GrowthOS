package com.growthos.app.ui.knowledge

import com.growthos.app.data.local.dao.DomainDao
import com.growthos.app.data.local.dao.KnowledgeDao
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.Knowledge
import com.growthos.app.data.local.relation.KnowledgeWithDomainName
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.KnowledgeRepository
import com.growthos.app.domain.model.KnowledgeType
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [KnowledgeListViewModel] 单测。仿 [com.growthos.app.ui.principle.PrincipleListViewModelTest]:
 * 内存假 DAO 喂真 Repository,StandardTestDispatcher 控时序。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KnowledgeListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm(knowledgeDao: FakeKnowledgeDao): KnowledgeListViewModel =
        KnowledgeListViewModel(
            knowledgeRepository = KnowledgeRepository(knowledgeDao),
            domainRepository = DomainRepository(FakeDomainDaoForList())
        )

    @Test
    fun `empty list yields empty state`() = runTest(testDispatcher) {
        val vm = newVm(FakeKnowledgeDao())
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isEmpty)
    }

    @Test
    fun `knowledges populated from repository`() = runTest(testDispatcher) {
        val dao = FakeKnowledgeDao()
        dao.seed(Knowledge(id = 1, content = "经验1", type = KnowledgeType.EXPERIENCE, createdAt = 100L))
        dao.seed(Knowledge(id = 2, content = "待办1", type = KnowledgeType.TODO, createdAt = 200L))
        val vm = newVm(dao)
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.knowledges.size)
        // 按 createdAt 倒序:200L 在前
        assertEquals("待办1", vm.uiState.value.knowledges.first().knowledge.content)
    }

    @Test
    fun `delete removes knowledge`() = runTest(testDispatcher) {
        val dao = FakeKnowledgeDao()
        val k = Knowledge(id = 1, content = "要删的", type = KnowledgeType.EXPERIENCE, createdAt = 100L)
        dao.seed(k)
        val vm = newVm(dao)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.knowledges.size)

        vm.delete(k)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isEmpty)
    }

    @Test
    fun `toggleDone flips done state`() = runTest(testDispatcher) {
        val dao = FakeKnowledgeDao()
        dao.seed(Knowledge(id = 1, content = "待办", type = KnowledgeType.TODO, createdAt = 100L, done = false))
        val vm = newVm(dao)
        advanceUntilIdle()

        vm.toggleDone(dao.all.value.first())
        advanceUntilIdle()
        assertTrue(dao.all.value.first().done)
    }
}

/** 内存假 KnowledgeDao,MutableStateFlow 驱动。 */
internal class FakeKnowledgeDao : KnowledgeDao {
    val all = MutableStateFlow<List<Knowledge>>(emptyList())

    fun seed(k: Knowledge) { all.update { it + k } }

    // 导入 feature 扩展,Fake 不涉及,空实现。
    override suspend fun insertAll(knowledges: List<Knowledge>) {}

    override suspend fun deleteAll() {}

    override suspend fun countAll(): Int = 0

    override suspend fun insert(knowledge: Knowledge): Long {
        all.update { it + knowledge }
        return knowledge.id
    }

    override suspend fun update(knowledge: Knowledge) {
        all.update { list -> list.map { if (it.id == knowledge.id) knowledge else it } }
    }

    override suspend fun delete(knowledge: Knowledge) {
        all.update { list -> list.filterNot { it.id == knowledge.id } }
    }

    override fun observeAll(): Flow<List<Knowledge>> =
        all.map { it.sortedByDescending { k -> k.createdAt } }

    override fun observeByDomain(domainId: Long): Flow<List<Knowledge>> =
        all.map { it.filter { k -> k.domainId == domainId }.sortedByDescending { k -> k.createdAt } }

    override suspend fun getById(id: Long): Knowledge? = all.value.firstOrNull { it.id == id }

    override fun observeAllWithDomainName(): Flow<List<KnowledgeWithDomainName>> =
        all.map { list ->
            list.sortedByDescending { it.createdAt }
                .map { k -> KnowledgeWithDomainName(k, domainName = null) }
        }
}

/** 内存假 DomainDao,只实现 observeVisible(筛选条用)。 */
internal class FakeDomainDaoForList : DomainDao {
    private val all = MutableStateFlow<List<Domain>>(emptyList())

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
    override fun observeVisible(): Flow<List<Domain>> =
        all.map { it.filter { d -> !d.hidden } }
    override suspend fun getById(id: Long): Domain? = all.value.firstOrNull { it.id == id }
    override fun observeById(id: Long): Flow<Domain?> = all.map { it.firstOrNull { d -> d.id == id } }
    override suspend fun setHidden(id: Long, hidden: Boolean) {
        all.update { list -> list.map { if (it.id == id) it.copy(hidden = hidden) else it } }
    }
}
