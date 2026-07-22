package com.growthos.app.ui.training

import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.ErrorTypeRepository
import com.growthos.app.data.repository.TrainingRepository
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
 * [TrainingEditViewModel] 状态逻辑单测(二期阶段 5 / 设计 §测试)。
 *
 * 复用 [TrainingListViewModelTest] 同包的共享 Fake。预填 errorType(D6)、必填校验、
 * 保存调 create 且 startedAt=now/status=IN_PROGRESS、空可选字段存 null(D4 只新建)。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TrainingEditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm(
        trainingDao: FakeTrainingDao,
        errorTypeDao: FakeErrorTypeDao,
        domainDao: FakeDomainDao,
        store: FakeSelectedDomainStore,
        prefillErrorTypeId: Long?
    ): TrainingEditViewModel = TrainingEditViewModel(
        trainingRepository = TrainingRepository(trainingDao),
        errorTypeRepository = ErrorTypeRepository(errorTypeDao),
        domainRepository = DomainRepository(domainDao),
        selectedStore = store,
        prefillErrorTypeId = prefillErrorTypeId
    )

    @Test
    fun `prefill error type applied when present`() = runTest(testDispatcher) {
        val errorTypeDao = FakeErrorTypeDao().apply { seed(e1, "边界条件遗漏") }
        val domainDao = FakeDomainDao().apply { seed(d1, "编程") }
        val store = FakeSelectedDomainStore(initial = d1)

        val vm = newVm(FakeTrainingDao(), errorTypeDao, domainDao, store, prefillErrorTypeId = e1)
        advanceUntilIdle()
        assertEquals(e1, vm.uiState.value.form.errorTypeId)
        assertEquals(d1, vm.uiState.value.form.domainId)  // 取当前选中领域
    }

    @Test
    fun `prefill ignored when error type id not in list`() = runTest(testDispatcher) {
        val errorTypeDao = FakeErrorTypeDao().apply { seed(e1, "边界条件遗漏") }
        val domainDao = FakeDomainDao().apply { seed(d1, "编程") }
        val store = FakeSelectedDomainStore(initial = d1)

        val vm = newVm(FakeTrainingDao(), errorTypeDao, domainDao, store, prefillErrorTypeId = 999L)
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.form.errorTypeId)  // 999 不存在,不预填
    }

    @Test
    fun `canSave requires domain errorType and goal`() = runTest(testDispatcher) {
        val errorTypeDao = FakeErrorTypeDao().apply { seed(e1, "边界条件遗漏") }
        val domainDao = FakeDomainDao().apply { seed(d1, "编程") }
        val store = FakeSelectedDomainStore(initial = d1)
        val vm = newVm(FakeTrainingDao(), errorTypeDao, domainDao, store, prefillErrorTypeId = e1)
        advanceUntilIdle()

        // 预填了 domain + errorType,但 goal 空 → 不能保存
        assertFalse(vm.uiState.value.canSave)

        vm.updateGoal("练成边界清单")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun `save creates training with now startedAt and in_progress status`() = runTest(testDispatcher) {
        val trainingDao = FakeTrainingDao()
        val errorTypeDao = FakeErrorTypeDao().apply { seed(e1, "边界条件遗漏") }
        val domainDao = FakeDomainDao().apply { seed(d1, "编程") }
        val store = FakeSelectedDomainStore(initial = d1)
        val fixedNow = 123456789L
        val vm = TrainingEditViewModel(
            TrainingRepository(trainingDao),
            ErrorTypeRepository(errorTypeDao),
            DomainRepository(domainDao),
            store,
            prefillErrorTypeId = e1,
            now = { fixedNow }
        )
        advanceUntilIdle()
        vm.updateGoal("练成边界清单")
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        val created = trainingDao.observeAllWithNames().first().first().training
        assertEquals(d1, created.domainId)
        assertEquals(e1, created.errorTypeId)
        assertEquals("练成边界清单", created.goal)
        assertEquals(fixedNow, created.startedAt)
        assertEquals(com.growthos.app.domain.model.TrainingStatus.IN_PROGRESS, created.status)
        assertNull(created.endedAt)
    }

    @Test
    fun `blank optional fields stored as null`() = runTest(testDispatcher) {
        val trainingDao = FakeTrainingDao()
        val errorTypeDao = FakeErrorTypeDao().apply { seed(e1, "边界条件遗漏") }
        val domainDao = FakeDomainDao().apply { seed(d1, "编程") }
        val store = FakeSelectedDomainStore(initial = d1)
        val vm = newVm(trainingDao, errorTypeDao, domainDao, store, prefillErrorTypeId = e1)
        advanceUntilIdle()
        vm.updateGoal("目标")
        // 验收标准与备注留空
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        val created = trainingDao.observeAllWithNames().first().first().training
        assertNull(created.acceptanceCriteria)
        assertNull(created.note)
    }

    @Test
    fun `non-blank optional fields trimmed and stored`() = runTest(testDispatcher) {
        val trainingDao = FakeTrainingDao()
        val errorTypeDao = FakeErrorTypeDao().apply { seed(e1, "边界条件遗漏") }
        val domainDao = FakeDomainDao().apply { seed(d1, "编程") }
        val store = FakeSelectedDomainStore(initial = d1)
        val vm = newVm(trainingDao, errorTypeDao, domainDao, store, prefillErrorTypeId = e1)
        advanceUntilIdle()
        vm.updateGoal("目标")
        vm.updateAcceptanceCriteria("  连续3次不漏  ")
        vm.updateNote("  备注  ")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        val created = trainingDao.observeAllWithNames().first().first().training
        assertEquals("连续3次不漏", created.acceptanceCriteria)
        assertEquals("备注", created.note)
    }

    @Test
    fun `save emits Saved event`() = runTest(testDispatcher) {
        val trainingDao = FakeTrainingDao()
        val errorTypeDao = FakeErrorTypeDao().apply { seed(e1, "边界条件遗漏") }
        val domainDao = FakeDomainDao().apply { seed(d1, "编程") }
        val store = FakeSelectedDomainStore(initial = d1)
        val vm = newVm(trainingDao, errorTypeDao, domainDao, store, prefillErrorTypeId = e1)
        advanceUntilIdle()
        vm.updateGoal("目标")
        advanceUntilIdle()

        // SharedFlow 无 replay,需先订阅再 save 才能收到。
        val collected = mutableListOf<TrainingEditEvent>()
        val job = launch(testDispatcher) {
            vm.events.collect { collected.add(it) }
        }
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        job.cancel()

        assertTrue(collected.contains(TrainingEditEvent.Saved))
    }
}
