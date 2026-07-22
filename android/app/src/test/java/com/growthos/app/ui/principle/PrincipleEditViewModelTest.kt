package com.growthos.app.ui.principle

import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.ErrorTypeRepository
import com.growthos.app.data.repository.PrincipleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PrincipleEditViewModel] 状态逻辑单测(二期阶段 6 / 设计 §测试)。
 *
 * 复用 [PrincipleListViewModelTest] 同包的共享 Fake。新建/编辑共用一页(D2),
 * 内容必填,四关联可选,保存保留原 createdAt(D2)。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PrincipleEditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm(
        principleDao: FakePrincipleDao,
        domainDao: FakeDomainDao,
        errorTypeDao: FakeErrorTypeDao,
        principleId: Long?,
        prefillTrainingId: Long? = null,
        prefillSampleId: Long? = null
    ): PrincipleEditViewModel = PrincipleEditViewModel(
        principleRepository = PrincipleRepository(principleDao),
        domainRepository = DomainRepository(domainDao),
        errorTypeRepository = ErrorTypeRepository(errorTypeDao),
        principleId = principleId,
        prefillTrainingId = prefillTrainingId,
        prefillSampleId = prefillSampleId
    )

    @Test
    fun `new state isEditing false and canSave false`() = runTest(testDispatcher) {
        val vm = newVm(FakePrincipleDao(), FakeDomainDao(), FakeErrorTypeDao(), principleId = null)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isEditing)
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun `canSave true when content non-blank`() = runTest(testDispatcher) {
        val vm = newVm(FakePrincipleDao(), FakeDomainDao(), FakeErrorTypeDao(), principleId = null)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.canSave)

        vm.updateContent("边界先列清单")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun `editing prefills form from existing principle`() = runTest(testDispatcher) {
        val dao = FakePrincipleDao()
        dao.seedDomain(pDomain, "编程")
        dao.seedErrorType(pErrorType, "边界条件遗漏")
        dao.insert(makePrinciple(id = 1, content = "原内容", createdAt = 1000L, domainId = pDomain, errorTypeId = pErrorType))
        val vm = newVm(dao, FakeDomainDao(), FakeErrorTypeDao(), principleId = 1)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isEditing)
        assertEquals("原内容", vm.uiState.value.form.content)
        assertEquals(pDomain, vm.uiState.value.form.domainId)
        assertEquals(pErrorType, vm.uiState.value.form.errorTypeId)
    }

    @Test
    fun `save new creates principle with now createdAt`() = runTest(testDispatcher) {
        val dao = FakePrincipleDao()
        val fixedNow = 99999L
        val vm = PrincipleEditViewModel(
            PrincipleRepository(dao), DomainRepository(FakeDomainDao()),
            ErrorTypeRepository(FakeErrorTypeDao()),
            principleId = null, prefillTrainingId = null, prefillSampleId = null,
            now = { fixedNow }
        )
        advanceUntilIdle()
        vm.updateContent("新原则")
        vm.updateDomain(pDomain)
        vm.updateErrorType(pErrorType)
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        val created = dao.observeAllWithNames().first().first().principle
        assertEquals("新原则", created.content)
        assertEquals(pDomain, created.domainId)
        assertEquals(pErrorType, created.errorTypeId)
        assertEquals(fixedNow, created.createdAt)
    }

    @Test
    fun `save edit preserves original createdAt`() = runTest(testDispatcher) {
        val dao = FakePrincipleDao()
        dao.insert(makePrinciple(id = 1, content = "原内容", createdAt = 1000L))
        val vm = newVm(dao, FakeDomainDao(), FakeErrorTypeDao(), principleId = 1)
        advanceUntilIdle()

        vm.updateContent("改后内容")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        val updated = dao.getById(1)!!
        assertEquals("改后内容", updated.content)
        assertEquals(1000L, updated.createdAt)  // 保留原 createdAt
    }

    @Test
    fun `save edit updates associations`() = runTest(testDispatcher) {
        val dao = FakePrincipleDao()
        dao.insert(makePrinciple(id = 1, content = "原则", createdAt = 0L, domainId = pDomain))
        val vm = newVm(dao, FakeDomainDao(), FakeErrorTypeDao(), principleId = 1)
        advanceUntilIdle()

        vm.updateDomain(null)  // 清除领域关联
        vm.updateErrorType(pErrorType)  // 加错误类型
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        val updated = dao.getById(1)!!
        assertNull(updated.domainId)
        assertEquals(pErrorType, updated.errorTypeId)
    }

    @Test
    fun `prefill training and sample applied on new`() = runTest(testDispatcher) {
        val vm = newVm(FakePrincipleDao(), FakeDomainDao(), FakeErrorTypeDao(),
            principleId = null, prefillTrainingId = 5L, prefillSampleId = 6L)
        advanceUntilIdle()
        assertEquals(5L, vm.uiState.value.form.trainingId)
        assertEquals(6L, vm.uiState.value.form.sampleId)
    }

    @Test
    fun `save emits Saved event`() = runTest(testDispatcher) {
        val dao = FakePrincipleDao()
        val vm = newVm(dao, FakeDomainDao(), FakeErrorTypeDao(), principleId = null)
        advanceUntilIdle()
        vm.updateContent("原则")
        advanceUntilIdle()

        // SharedFlow 无 replay,需先订阅再 save。
        val collected = mutableListOf<PrincipleEditEvent>()
        val job = launch(testDispatcher) { vm.events.collect { collected.add(it) } }
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        job.cancel()

        assertTrue(collected.contains(PrincipleEditEvent.Saved))
    }

    @Test
    fun `delete emits Deleted event and only works in editing`() = runTest(testDispatcher) {
        val dao = FakePrincipleDao()
        dao.insert(makePrinciple(id = 1, content = "原则", createdAt = 0L))
        val vm = newVm(dao, FakeDomainDao(), FakeErrorTypeDao(), principleId = 1)
        advanceUntilIdle()

        val collected = mutableListOf<PrincipleEditEvent>()
        val job = launch(testDispatcher) { vm.events.collect { collected.add(it) } }
        advanceUntilIdle()
        vm.delete()
        advanceUntilIdle()
        job.cancel()

        assertTrue(collected.contains(PrincipleEditEvent.Deleted))
        assertNull(dao.getById(1))  // 已删
    }

    @Test
    fun `update domain to null clears association`() = runTest(testDispatcher) {
        val dao = FakePrincipleDao()
        dao.insert(makePrinciple(id = 1, content = "原则", createdAt = 0L, domainId = pDomain))
        val vm = newVm(dao, FakeDomainDao(), FakeErrorTypeDao(), principleId = 1)
        advanceUntilIdle()
        assertEquals(pDomain, vm.uiState.value.form.domainId)

        vm.updateDomain(null)
        advanceUntilIdle()
        assertNull(vm.uiState.value.form.domainId)
    }
}
