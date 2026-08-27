package com.growthos.app.ui.domain_view

import com.growthos.app.data.local.SelectedDomainStore
import com.growthos.app.data.local.dao.KnowledgeDao
import com.growthos.app.data.local.dao.PrincipleDao
import com.growthos.app.data.local.dao.SampleDao
import com.growthos.app.data.local.dao.TrainingDao
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.data.local.entity.Knowledge
import com.growthos.app.data.local.entity.Principle
import com.growthos.app.data.local.entity.Sample
import com.growthos.app.data.local.entity.Training
import com.growthos.app.data.local.relation.ControllableRatio
import com.growthos.app.data.local.relation.ErrorTypeCount
import com.growthos.app.data.local.relation.KnowledgeWithDomainName
import com.growthos.app.data.local.relation.SampleWithErrorType
import com.growthos.app.data.local.relation.TrainingEffectStats
import com.growthos.app.data.local.relation.TrainingWithTypeName
import com.growthos.app.data.repository.KnowledgeRepository
import com.growthos.app.data.repository.PrincipleRepository
import com.growthos.app.data.repository.SampleRepository
import com.growthos.app.data.repository.TrainingRepository
import com.growthos.app.domain.model.Attribution
import com.growthos.app.domain.model.KnowledgeType
import com.growthos.app.domain.model.TrainingStatus
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [DomainStatsViewModel] 状态逻辑单测(二期阶段 3 / 设计 §测试)。
 *
 * 套路同 DomainViewModelTest / SampleViewModelTest:内存假 DAO/Store 喂真 Repository,
 * StandardTestDispatcher 控时序,不引 truth/turbine(踩坑 P10)。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DomainStatsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm(
        sampleDao: FakeSampleDao,
        trainingDao: FakeTrainingDao,
        principleDao: FakePrincipleDao,
        store: FakeSelectedDomainStore
    ): DomainStatsViewModel = DomainStatsViewModel(
        sampleRepository = SampleRepository(sampleDao),
        trainingRepository = TrainingRepository(trainingDao),
        principleRepository = PrincipleRepository(principleDao),
        knowledgeRepository = KnowledgeRepository(FakeKnowledgeDao()),
        selectedStore = store
    )

    @Test
    fun `no selected domain yields empty state with hasDomain false`() = runTest(testDispatcher) {
        val vm = newVm(FakeSampleDao(), FakeTrainingDao(), FakePrincipleDao(), FakeSelectedDomainStore(initial = null))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertFalse(state.hasDomain)
        assertTrue(state.recentSamples.isEmpty())
        assertTrue(state.errorDistribution.isEmpty())
        assertTrue(state.filteredSamples.isEmpty())
    }

    @Test
    fun `stats populate for selected domain`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        val trainingDao = FakeTrainingDao()
        val principleDao = FakePrincipleDao()
        seedSamples(sampleDao, domainId = 1, errorTypeId = 10, count = 3)
        seedTraining(trainingDao, domainId = 1, errorTypeId = 10, goal = "先列状态表")
        principleDao.insert(Principle(id = 1, content = "边界先列清单", createdAt = 0, domainId = 1))

        val vm = newVm(sampleDao, trainingDao, principleDao, FakeSelectedDomainStore(initial = 1L))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.hasDomain)
        assertEquals(3, state.recentSamples.size)
        assertEquals(3, state.errorDistribution.first().count)
        assertEquals(1, state.inProgressTrainings.size)
        assertEquals("先列状态表", state.inProgressTrainings.first().training.goal)
        assertEquals(1, state.recentPrinciples.size)
        // 默认无筛选 → filteredSamples = 全量(3)
        assertEquals(3, state.filteredSamples.size)
    }

    @Test
    fun `switching domain refreshes stats`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        val trainingDao = FakeTrainingDao()
        val principleDao = FakePrincipleDao()
        seedSamples(sampleDao, domainId = 1, errorTypeId = 10, count = 2)
        seedSamples(sampleDao, domainId = 2, errorTypeId = 20, count = 5)
        val store = FakeSelectedDomainStore(initial = 1L)

        val vm = newVm(sampleDao, trainingDao, principleDao, store)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.recentSamples.size)

        store.set(2L)
        advanceUntilIdle()
        assertEquals(5, vm.uiState.value.recentSamples.size)
    }

    @Test
    fun `filter by error type narrows filteredSamples`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        seedSamples(sampleDao, domainId = 1, errorTypeId = 10, count = 3)
        seedSamples(sampleDao, domainId = 1, errorTypeId = 20, count = 2)
        val vm = newVm(sampleDao, FakeTrainingDao(), FakePrincipleDao(), FakeSelectedDomainStore(initial = 1L))
        advanceUntilIdle()
        assertEquals(5, vm.uiState.value.filteredSamples.size)

        vm.filterByErrorType(10L)
        advanceUntilIdle()
        val filtered = vm.uiState.value.filteredSamples
        assertEquals(3, filtered.size)
        assertTrue(filtered.all { it.sample.errorTypeId == 10L })
    }

    @Test
    fun `filter by attribution narrows filteredSamples`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        // 2 可控 + 1 不可控
        sampleDao.insert(makeSample(domainId = 1, errorTypeId = 10, Attribution.CONTROLLABLE, time = 1L))
        sampleDao.insert(makeSample(domainId = 1, errorTypeId = 10, Attribution.CONTROLLABLE, time = 2L))
        sampleDao.insert(makeSample(domainId = 1, errorTypeId = 10, Attribution.UNCONTROLLABLE, time = 3L))
        val vm = newVm(sampleDao, FakeTrainingDao(), FakePrincipleDao(), FakeSelectedDomainStore(initial = 1L))
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.filteredSamples.size)

        vm.filterByAttribution(Attribution.CONTROLLABLE)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.filteredSamples.size)
        assertTrue(vm.uiState.value.filteredSamples.all { it.sample.attribution == Attribution.CONTROLLABLE })
    }

    @Test
    fun `filter by both error type and attribution stacks`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        sampleDao.insert(makeSample(domainId = 1, errorTypeId = 10, Attribution.CONTROLLABLE, time = 1L))
        sampleDao.insert(makeSample(domainId = 1, errorTypeId = 10, Attribution.UNCONTROLLABLE, time = 2L))
        sampleDao.insert(makeSample(domainId = 1, errorTypeId = 20, Attribution.CONTROLLABLE, time = 3L))
        val vm = newVm(sampleDao, FakeTrainingDao(), FakePrincipleDao(), FakeSelectedDomainStore(initial = 1L))
        advanceUntilIdle()

        vm.filterByErrorType(10L)
        vm.filterByAttribution(Attribution.CONTROLLABLE)
        advanceUntilIdle()
        val filtered = vm.uiState.value.filteredSamples
        assertEquals(1, filtered.size)
        assertEquals(10L, filtered.first().sample.errorTypeId)
        assertEquals(Attribution.CONTROLLABLE, filtered.first().sample.attribution)
    }

    @Test
    fun `clearFilter restores all samples`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        seedSamples(sampleDao, domainId = 1, errorTypeId = 10, count = 3)
        val vm = newVm(sampleDao, FakeTrainingDao(), FakePrincipleDao(), FakeSelectedDomainStore(initial = 1L))
        advanceUntilIdle()
        vm.filterByErrorType(10L)
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.filteredSamples.size) // 本就全是 10

        vm.filterByErrorType(999L) // 筛选不存在的
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.filteredSamples.size)

        vm.clearFilter()
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.filteredSamples.size)
    }

    @Test
    fun `availableErrorTypes equals errorDistribution`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        seedSamples(sampleDao, domainId = 1, errorTypeId = 10, count = 3)
        seedSamples(sampleDao, domainId = 1, errorTypeId = 20, count = 1)
        val vm = newVm(sampleDao, FakeTrainingDao(), FakePrincipleDao(), FakeSelectedDomainStore(initial = 1L))
        advanceUntilIdle()
        assertEquals(vm.uiState.value.errorDistribution, vm.uiState.value.availableErrorTypes)
    }

    // ---------- 辅助 ----------

    private suspend fun seedSamples(dao: FakeSampleDao, domainId: Long, errorTypeId: Long, count: Int) {
        repeat(count) { i ->
            dao.insert(makeSample(domainId, errorTypeId, Attribution.CONTROLLABLE, time = 100L + i))
        }
    }

    private suspend fun seedTraining(dao: FakeTrainingDao, domainId: Long, errorTypeId: Long, goal: String) {
        dao.insert(
            Training(
                id = 0, domainId = domainId, errorTypeId = errorTypeId, goal = goal,
                acceptanceCriteria = null, startedAt = 0, endedAt = null,
                status = TrainingStatus.IN_PROGRESS, note = null
            )
        )
    }

    private fun makeSample(domainId: Long, errorTypeId: Long, attribution: Attribution, time: Long) = Sample(
        domainId = domainId, recordedAt = time, result = "结果",
        errorTypeId = errorTypeId, attribution = attribution, emotionIntensity = null, review = "复盘"
    )
}

/** 内存假 Store:同前两阶段。 */
private class FakeSelectedDomainStore(initial: Long?) : SelectedDomainStore {
    private val _state = MutableStateFlow(initial)
    override val flow: Flow<Long?> = _state.asStateFlow()
    override suspend fun set(id: Long?) { _state.value = id }
}

/**
 * 内存假 SampleDao:只实现 ViewModel 调用的 observeRecentByDomain / observeTopErrorTypes /
 * observeWithNames / observeByDomain。其余抛 NotImplementedError。
 * observeWithNames 用内置 ErrorType 名(测试固定 id→name 映射)。
 */
private class FakeSampleDao : SampleDao {
    private val all = MutableStateFlow<List<Sample>>(emptyList())
    private var counter = 0L

    override suspend fun insert(sample: Sample): Long {
        val id = if (sample.id == 0L) { counter += 1; counter } else sample.id
        all.update { it + sample.copy(id = id) }
        return id
    }

    override suspend fun update(sample: Sample) {
        all.update { list -> list.map { if (it.id == sample.id) sample else it } }
    }

    override suspend fun delete(sample: Sample) {
        all.update { list -> list.filterNot { it.id == sample.id } }
    }

    override suspend fun getById(id: Long): Sample? = all.value.firstOrNull { it.id == id }
    override fun observeById(id: Long): Flow<Sample?> = all.map { it.firstOrNull { s -> s.id == id } }

    override fun observeToday(startOfToday: Long, startOfNextDay: Long): Flow<List<Sample>> =
        throw NotImplementedError()

    override fun observeAll(): Flow<List<Sample>> = all.map { it.sortedByDescending { s -> s.recordedAt } }

    override fun observeByDomain(domainId: Long): Flow<List<Sample>> =
        all.map { it.filter { s -> s.domainId == domainId }.sortedByDescending { s -> s.recordedAt } }

    override fun observeByErrorType(errorTypeId: Long): Flow<List<Sample>> =
        all.map { it.filter { s -> s.errorTypeId == errorTypeId }.sortedByDescending { s -> s.recordedAt } }

    override fun observeByAttribution(attribution: String): Flow<List<Sample>> =
        all.map { it.filter { s -> s.attribution.name == attribution }.sortedByDescending { s -> s.recordedAt } }

    override fun observeWithNames(domainId: Long, startMillis: Long, endMillis: Long): Flow<List<SampleWithErrorType>> =
        all.map { list ->
            list.filter { it.domainId == domainId }
                .sortedByDescending { it.recordedAt }
                .map { s -> SampleWithErrorType(s, errorTypeName = "错误类型${s.errorTypeId}", domainName = "领域${s.domainId}") }
        }

    override fun observeRecentByDomain(domainId: Long, limit: Int): Flow<List<SampleWithErrorType>> =
        all.map { list ->
            list.filter { it.domainId == domainId }
                .sortedByDescending { it.recordedAt }
                .take(limit)
                .map { s -> SampleWithErrorType(s, errorTypeName = "错误类型${s.errorTypeId}", domainName = "领域${s.domainId}") }
        }

    override fun observeTopErrorTypes(domainId: Long, startMillis: Long, endMillis: Long, limit: Int): Flow<List<ErrorTypeCount>> =
        all.map { list ->
            list.filter { it.domainId == domainId }
                .groupBy { it.errorTypeId }
                .map { (eid, samples) -> ErrorTypeCount(eid, "错误类型$eid", samples.size) }
                .sortedByDescending { it.count }
                .take(limit)
        }

    override fun observeControllableRatio(domainId: Long, startMillis: Long, endMillis: Long): Flow<ControllableRatio?> =
        throw NotImplementedError()

    override fun observeHighestEmotion(domainId: Long, startMillis: Long, endMillis: Long): Flow<SampleWithErrorType?> =
        throw NotImplementedError()

    override fun observeSamplesAfter(errorTypeId: Long, startedAt: Long): Flow<List<SampleWithErrorType>> =
        throw NotImplementedError()

    override suspend fun countByDomain(domainId: Long): Int = all.value.count { it.domainId == domainId }

    // 阶段 4 周复盘新增查询,本 ViewModel 不调用 → 抛 NotImplementedError 占位。
    override fun observeCount(domainId: Long, startMillis: Long, endMillis: Long): Flow<Int> =
        throw NotImplementedError()

    override fun observeTopControllableErrorType(
        domainId: Long, startMillis: Long, endMillis: Long
    ): Flow<ErrorTypeCount?> = throw NotImplementedError()
}

/** 内存假 TrainingDao:只实现 observeInProgressByDomainWithTypeName。 */
private class FakeTrainingDao : TrainingDao {
    private val all = MutableStateFlow<List<Training>>(emptyList())
    private var counter = 0L

    fun nextId(): Long { counter += 1; return counter }

    override suspend fun insert(training: Training): Long {
        val id = if (training.id == 0L) nextId() else training.id
        all.update { it + training.copy(id = id) }
        return id
    }

    override suspend fun update(training: Training) {
        all.update { list -> list.map { if (it.id == training.id) training else it } }
    }

    override suspend fun delete(training: Training) {
        all.update { list -> list.filterNot { it.id == training.id } }
    }

    override suspend fun getById(id: Long): Training? = all.value.firstOrNull { it.id == id }

    override fun observeInProgress(): Flow<List<Training>> =
        all.map { it.filter { t -> t.status == TrainingStatus.IN_PROGRESS }.sortedByDescending { t -> t.startedAt } }

    override fun observeByDomain(domainId: Long): Flow<List<Training>> =
        all.map { it.filter { t -> t.domainId == domainId }.sortedByDescending { t -> t.startedAt } }

    override fun observeByErrorType(errorTypeId: Long): Flow<List<Training>> =
        all.map { it.filter { t -> t.errorTypeId == errorTypeId }.sortedByDescending { t -> t.startedAt } }

    override fun observeInProgressByDomainWithTypeName(domainId: Long): Flow<List<TrainingWithTypeName>> =
        all.map { list ->
            list.filter { it.domainId == domainId && it.status == TrainingStatus.IN_PROGRESS }
                .sortedByDescending { it.startedAt }
                .map { t -> TrainingWithTypeName(t, errorTypeName = "错误类型${t.errorTypeId}") }
        }

    override suspend fun effectStats(errorTypeId: Long, startedAt: Long): TrainingEffectStats =
        throw NotImplementedError()

    // 阶段 5 训练项列表新增查询,本 ViewModel 不调用 → 抛 NotImplementedError 占位。
    override fun observeAllWithNames(): Flow<List<com.growthos.app.data.local.relation.TrainingWithNames>> =
        throw NotImplementedError()
}

/** 内存假 PrincipleDao:只实现 observeByDomain。 */
private class FakePrincipleDao : PrincipleDao {
    private val all = MutableStateFlow<List<Principle>>(emptyList())

    override suspend fun insert(principle: Principle): Long {
        all.update { it + principle }
        return principle.id
    }

    override suspend fun update(principle: Principle) {
        all.update { list -> list.map { if (it.id == principle.id) principle else it } }
    }

    override suspend fun delete(principle: Principle) {
        all.update { list -> list.filterNot { it.id == principle.id } }
    }

    override fun observeAll(): Flow<List<Principle>> = all.map { it.sortedByDescending { p -> p.createdAt } }
    override fun observeByDomain(domainId: Long): Flow<List<Principle>> =
        all.map { it.filter { p -> p.domainId == domainId }.sortedByDescending { p -> p.createdAt } }
    override suspend fun getById(id: Long): Principle? = all.value.firstOrNull { it.id == id }

    // 阶段 6 原则列表新增查询,本 ViewModel 不调用 → 抛 NotImplementedError 占位。
    override fun observeAllWithNames(): Flow<List<com.growthos.app.data.local.relation.PrincipleWithNames>> =
        throw NotImplementedError()
}

/** 内存假 KnowledgeDao:DomainStatsViewModel 用到 observeByDomain,其余留空。 */
internal class FakeKnowledgeDao : KnowledgeDao {
    private val all = MutableStateFlow<List<Knowledge>>(emptyList())

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
