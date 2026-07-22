package com.growthos.app.ui.training

import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.ErrorTypeRepository
import com.growthos.app.data.repository.ErrorTypeRepositoryImpl
import com.growthos.app.data.repository.SampleRepository
import com.growthos.app.data.repository.TrainingRepository
import com.growthos.app.domain.model.Attribution
import com.growthos.app.domain.model.TrainingStatus
import kotlinx.coroutines.Dispatchers
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
 * [TrainingEffectViewModel] 状态逻辑单测(二期阶段 5 / 设计 §测试)。
 *
 * 复用 [TrainingListViewModelTest] 同包的共享 Fake。effectStats 是 suspend 一次性,
 * afterSamples 是 Flow;training 一次性加载后驱动两者(设计 D2)。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TrainingEffectViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm(
        trainingDao: FakeTrainingDao,
        sampleDao: FakeSampleDao,
        errorTypeDao: FakeErrorTypeDao,
        domainDao: FakeDomainDao,
        trainingId: Long
    ): TrainingEffectViewModel {
        trainingDao.sampleProvider = { sampleDao.all.value }
        return TrainingEffectViewModel(
            trainingRepository = TrainingRepository(trainingDao),
            sampleRepository = SampleRepository(sampleDao),
            errorTypeRepository = ErrorTypeRepositoryImpl(errorTypeDao),
            domainRepository = DomainRepository(domainDao),
            trainingId = trainingId
        )
    }

    @Test
    fun `loads training and resolves names`() = runTest(testDispatcher) {
        val trainingDao = FakeTrainingDao()
        val sampleDao = FakeSampleDao()
        val errorTypeDao = FakeErrorTypeDao().apply { seed(e1, "边界条件遗漏") }
        val domainDao = FakeDomainDao().apply { seed(d1, "编程") }
        trainingDao.insert(makeTraining(d1, e1, TrainingStatus.IN_PROGRESS, startedAt = 1000L, id = 1))

        val vm = newVm(trainingDao, sampleDao, errorTypeDao, domainDao, trainingId = 1)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.loaded)
        assertEquals(1L, state.training?.id)
        assertEquals("边界条件遗漏", state.errorTypeName)
        assertEquals("编程", state.domainName)
    }

    @Test
    fun `effect stats counts before and after startedAt`() = runTest(testDispatcher) {
        val trainingDao = FakeTrainingDao()
        val sampleDao = FakeSampleDao()
        // 训练开始于 500;前 3 条(< 500),后 2 条(>= 500)
        sampleDao.insert(makeSample(d1, e1, Attribution.CONTROLLABLE, time = 100L))
        sampleDao.insert(makeSample(d1, e1, Attribution.CONTROLLABLE, time = 200L))
        sampleDao.insert(makeSample(d1, e1, Attribution.CONTROLLABLE, time = 300L))
        sampleDao.insert(makeSample(d1, e1, Attribution.CONTROLLABLE, time = 600L))
        sampleDao.insert(makeSample(d1, e1, Attribution.CONTROLLABLE, time = 700L))
        trainingDao.insert(makeTraining(d1, e1, TrainingStatus.IN_PROGRESS, startedAt = 500L, id = 1))

        val vm = newVm(trainingDao, sampleDao, FakeErrorTypeDao(), FakeDomainDao(), trainingId = 1)
        advanceUntilIdle()
        val stats = vm.uiState.value.stats!!
        assertEquals(3, stats.beforeCount)
        assertEquals(2, stats.afterCount)
    }

    @Test
    fun `after samples only includes samples after startedAt`() = runTest(testDispatcher) {
        val trainingDao = FakeTrainingDao()
        val sampleDao = FakeSampleDao()
        sampleDao.insert(makeSample(d1, e1, Attribution.CONTROLLABLE, time = 100L))  // 训练前
        sampleDao.insert(makeSample(d1, e1, Attribution.CONTROLLABLE, time = 600L))  // 训练后
        sampleDao.insert(makeSample(d1, e1, Attribution.CONTROLLABLE, time = 700L))  // 训练后
        trainingDao.insert(makeTraining(d1, e1, TrainingStatus.IN_PROGRESS, startedAt = 500L, id = 1))

        val vm = newVm(trainingDao, sampleDao, FakeErrorTypeDao(), FakeDomainDao(), trainingId = 1)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.afterSamples.size)
    }

    @Test
    fun `no samples after training yields empty list`() = runTest(testDispatcher) {
        val trainingDao = FakeTrainingDao()
        val sampleDao = FakeSampleDao()
        sampleDao.insert(makeSample(d1, e1, Attribution.CONTROLLABLE, time = 100L))  // 只有训练前
        trainingDao.insert(makeTraining(d1, e1, TrainingStatus.IN_PROGRESS, startedAt = 500L, id = 1))

        val vm = newVm(trainingDao, sampleDao, FakeErrorTypeDao(), FakeDomainDao(), trainingId = 1)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.afterSamples.isEmpty())
        assertEquals(0, vm.uiState.value.stats?.afterCount)
    }

    @Test
    fun `nonexistent training yields not loaded`() = runTest(testDispatcher) {
        val vm = newVm(FakeTrainingDao(), FakeSampleDao(), FakeErrorTypeDao(), FakeDomainDao(), trainingId = 999L)
        advanceUntilIdle()
        // getById 返回 null → training 仍为 null,loaded=false
        assertEquals(null, vm.uiState.value.training)
        assertTrue(!vm.uiState.value.loaded)
    }
}
