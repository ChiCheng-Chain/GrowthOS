package com.growthos.app.ui.knowledge

import com.growthos.app.data.local.dao.DomainDao
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.Knowledge
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.KnowledgeRepository
import com.growthos.app.domain.model.KnowledgeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
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
 * [KnowledgeEditViewModel] 单测。仿 [com.growthos.app.ui.principle.PrincipleEditViewModelTest]。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KnowledgeEditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedNow = 1_700_000_000_000L

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm(
        knowledgeDao: FakeKnowledgeDao,
        domainDao: FakeDomainDao,
        knowledgeId: Long? = null
    ): KnowledgeEditViewModel = KnowledgeEditViewModel(
        knowledgeRepository = KnowledgeRepository(knowledgeDao),
        domainRepository = DomainRepository(domainDao),
        knowledgeId = knowledgeId,
        now = { fixedNow }
    )

    @Test
    fun `new knowledge defaults to experience type`() = runTest(testDispatcher) {
        val vm = newVm(FakeKnowledgeDao(), FakeDomainDao())
        advanceUntilIdle()
        assertEquals(KnowledgeType.EXPERIENCE, vm.uiState.value.form.type)
        assertTrue(!vm.uiState.value.form.isValid) // content 空
    }

    @Test
    fun `edit prefills form from existing knowledge`() = runTest(testDispatcher) {
        val knowledgeDao = FakeKnowledgeDao()
        knowledgeDao.seed(
            Knowledge(id = 1, content = "原有待办", type = KnowledgeType.TODO,
                createdAt = 1000L, domainId = 1L, done = true)
        )
        val vm = newVm(knowledgeDao, FakeDomainDao(), knowledgeId = 1L)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isEditing)
        assertEquals("原有待办", vm.uiState.value.form.content)
        assertEquals(KnowledgeType.TODO, vm.uiState.value.form.type)
    }

    @Test
    fun `save inserts new knowledge with createdAt = now`() = runTest(testDispatcher) {
        val knowledgeDao = FakeKnowledgeDao()
        val vm = newVm(knowledgeDao, FakeDomainDao())
        advanceUntilIdle()

        vm.updateContent("新知识")
        vm.updateType(KnowledgeType.TODO)
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        assertEquals(1, knowledgeDao.all.value.size)
        val saved = knowledgeDao.all.value.first()
        assertEquals("新知识", saved.content)
        assertEquals(KnowledgeType.TODO, saved.type)
        assertEquals(fixedNow, saved.createdAt)
    }

    @Test
    fun `edit save preserves original createdAt`() = runTest(testDispatcher) {
        val knowledgeDao = FakeKnowledgeDao()
        val originalTime = 1000L
        knowledgeDao.seed(
            Knowledge(id = 1, content = "原", type = KnowledgeType.EXPERIENCE, createdAt = originalTime)
        )
        val vm = newVm(knowledgeDao, FakeDomainDao(), knowledgeId = 1L)
        advanceUntilIdle()
        vm.updateContent("改后")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        val updated = knowledgeDao.all.value.first { it.id == 1L }
        assertEquals(originalTime, updated.createdAt)
        assertEquals("改后", updated.content)
    }

    @Test
    fun `save emits Saved event`() = runTest(testDispatcher) {
        val knowledgeDao = FakeKnowledgeDao()
        val vm = newVm(knowledgeDao, FakeDomainDao())
        advanceUntilIdle()

        val collected = mutableListOf<KnowledgeEditEvent>()
        val job = launch { vm.events.toList(collected) }
        vm.updateContent("测试")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        job.cancel()

        assertTrue(collected.contains(KnowledgeEditEvent.Saved))
    }

    @Test
    fun `delete removes knowledge and emits Deleted`() = runTest(testDispatcher) {
        val knowledgeDao = FakeKnowledgeDao()
        knowledgeDao.seed(Knowledge(id = 1, content = "待删", type = KnowledgeType.EXPERIENCE, createdAt = 100L))
        val vm = newVm(knowledgeDao, FakeDomainDao(), knowledgeId = 1L)
        advanceUntilIdle()

        val collected = mutableListOf<KnowledgeEditEvent>()
        val job = launch { vm.events.toList(collected) }
        advanceUntilIdle()
        vm.delete()
        advanceUntilIdle()
        job.cancel()

        assertTrue(collected.contains(KnowledgeEditEvent.Deleted))
        assertTrue(knowledgeDao.all.value.none { it.id == 1L })
    }

    @Test
    fun `type can be switched to todo`() = runTest(testDispatcher) {
        val vm = newVm(FakeKnowledgeDao(), FakeDomainDao())
        advanceUntilIdle()
        assertEquals(KnowledgeType.EXPERIENCE, vm.uiState.value.form.type)

        vm.updateType(KnowledgeType.TODO)
        advanceUntilIdle()
        assertEquals(KnowledgeType.TODO, vm.uiState.value.form.type)
    }
}

/** 内存假 DomainDao,只实现 observeVisible(编辑页领域 chips 用)。 */
private class FakeDomainDao : DomainDao {
    private val all = MutableStateFlow<List<Domain>>(emptyList())

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
