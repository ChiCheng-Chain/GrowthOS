package com.growthos.app.ui.training

import com.growthos.app.data.local.SelectedDomainStore
import com.growthos.app.data.local.dao.DomainDao
import com.growthos.app.data.local.dao.ErrorTypeDao
import com.growthos.app.data.local.dao.SampleDao
import com.growthos.app.data.local.dao.TrainingDao
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.data.local.entity.Sample
import com.growthos.app.data.local.entity.Training
import com.growthos.app.data.local.relation.ControllableRatio
import com.growthos.app.data.local.relation.ErrorTypeCount
import com.growthos.app.data.local.relation.SampleWithErrorType
import com.growthos.app.data.local.relation.TrainingEffectStats
import com.growthos.app.data.local.relation.TrainingWithNames
import com.growthos.app.data.local.relation.TrainingWithTypeName
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.ErrorTypeRepository
import com.growthos.app.data.repository.SampleRepository
import com.growthos.app.data.repository.TrainingRepository
import com.growthos.app.domain.model.Attribution
import com.growthos.app.domain.model.TrainingStatus
import com.growthos.app.util.TimeUtil
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
 * [TrainingListViewModel] 状态逻辑单测(二期阶段 5 / 设计 §测试)。
 *
 * 套路同 DomainStatsViewModelTest:内存假 DAO 喂真 Repository,StandardTestDispatcher
 * 控时序,不引 truth/turbine(踩坑 P10)。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TrainingListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm(dao: FakeTrainingDao): TrainingListViewModel =
        TrainingListViewModel(TrainingRepository(dao))

    @Test
    fun `empty list yields empty state`() = runTest(testDispatcher) {
        val vm = newVm(FakeTrainingDao())
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isEmpty)
    }

    @Test
    fun `trainings ordered in_progress first then by startedAt desc`() = runTest(testDispatcher) {
        val dao = FakeTrainingDao()
        // 已完成(早)→ 进行中(早)→ 进行中(晚)→ 已放弃
        dao.insert(makeTraining(d1, e1, status = TrainingStatus.COMPLETED, startedAt = 100L, id = 1))
        dao.insert(makeTraining(d1, e1, status = TrainingStatus.IN_PROGRESS, startedAt = 200L, id = 2))
        dao.insert(makeTraining(d1, e1, status = TrainingStatus.IN_PROGRESS, startedAt = 300L, id = 3))
        dao.insert(makeTraining(d1, e1, status = TrainingStatus.ABANDONED, startedAt = 400L, id = 4))

        val vm = newVm(dao)
        advanceUntilIdle()
        val list = vm.uiState.value.trainings
        assertEquals(4, list.size)
        // 进行中在前(晚的先),然后已完成,最后已放弃
        assertEquals(3L, list[0].training.id)
        assertEquals(2L, list[1].training.id)
        assertEquals(1L, list[2].training.id)
        assertEquals(4L, list[3].training.id)
    }

    @Test
    fun `join resolves error type and domain names`() = runTest(testDispatcher) {
        val dao = FakeTrainingDao()
        dao.insert(makeTraining(d1, e1, status = TrainingStatus.IN_PROGRESS, startedAt = 100L, id = 1))
        val vm = newVm(dao)
        advanceUntilIdle()
        val row = vm.uiState.value.trainings.first()
        assertEquals("错误类型$e1", row.errorTypeName)
        assertEquals("领域$d1", row.domainName)
    }

    @Test
    fun `finish moves in_progress to completed and records endedAt`() = runTest(testDispatcher) {
        val dao = FakeTrainingDao()
        dao.insert(makeTraining(d1, e1, status = TrainingStatus.IN_PROGRESS, startedAt = 100L, id = 1))
        val vm = newVm(dao)
        advanceUntilIdle()
        assertEquals(TrainingStatus.IN_PROGRESS, vm.uiState.value.trainings.first().training.status)

        vm.finishTraining(1L, TrainingStatus.COMPLETED)
        advanceUntilIdle()
        val t = vm.uiState.value.trainings.first().training
        assertEquals(TrainingStatus.COMPLETED, t.status)
        assertTrue(t.endedAt != null)
    }

    @Test
    fun `finish with abandoned sets abandoned status`() = runTest(testDispatcher) {
        val dao = FakeTrainingDao()
        dao.insert(makeTraining(d1, e1, status = TrainingStatus.IN_PROGRESS, startedAt = 100L, id = 1))
        val vm = newVm(dao)
        advanceUntilIdle()

        vm.finishTraining(1L, TrainingStatus.ABANDONED)
        advanceUntilIdle()
        assertEquals(TrainingStatus.ABANDONED, vm.uiState.value.trainings.first().training.status)
    }
}

// ---------- 共享 Fake(供三个 Training ViewModelTest 复用) ----------

internal const val d1 = 1L
internal const val d2 = 2L
internal const val e1 = 10L
internal const val e2 = 20L

internal fun makeTraining(
    domainId: Long,
    errorTypeId: Long,
    status: TrainingStatus,
    startedAt: Long,
    id: Long = 0
) = Training(
    id = id, domainId = domainId, errorTypeId = errorTypeId, goal = "目标",
    acceptanceCriteria = null, startedAt = startedAt, endedAt = null,
    status = status, note = null
)

internal fun makeSample(
    domainId: Long,
    errorTypeId: Long,
    attribution: Attribution,
    time: Long,
    emotion: Int? = null
) = Sample(
    domainId = domainId, recordedAt = time, result = "结果", description = "描述",
    errorTypeId = errorTypeId, attribution = attribution, emotionIntensity = emotion, review = "复盘"
)

/** 内存假 TrainingDao:实现 observeAllWithNames / getById / insert / update(finish 用)。
 * effectStats 基于 [sampleProvider] 算(真实实现是 SQL 子查询查 samples 表,
 * Fake 里从外部注入样本列表,由测试把 FakeSampleDao 的内容接进来)。 */
internal class FakeTrainingDao : TrainingDao {
    private val all = MutableStateFlow<List<Training>>(emptyList())
    private var counter = 0L

    /** 供 effectStats 计数的样本来源(测试注入 FakeSampleDao.all)。 */
    var sampleProvider: () -> List<Sample> = { emptyList() }

    override suspend fun insert(training: Training): Long {
        val id = if (training.id == 0L) { counter += 1; counter } else training.id
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

    override suspend fun effectStats(errorTypeId: Long, startedAt: Long): TrainingEffectStats {
        val samples = sampleProvider().filter { it.errorTypeId == errorTypeId }
        val before = samples.count { it.recordedAt < startedAt }
        val after = samples.count { it.recordedAt >= startedAt }
        return TrainingEffectStats(before, after)
    }

    override fun observeAllWithNames(): Flow<List<TrainingWithNames>> =
        all.map { list ->
            list.sortedWith(
                compareBy<Training> { t ->
                    when (t.status) {
                        TrainingStatus.IN_PROGRESS -> 0
                        TrainingStatus.COMPLETED -> 1
                        TrainingStatus.ABANDONED -> 2
                    }
                }.thenByDescending { it.startedAt }
            ).map { t -> TrainingWithNames(t, errorTypeName = "错误类型${t.errorTypeId}", domainName = "领域${t.domainId}") }
        }
}

/** 内存假 SampleDao:实现 TrainingEffectViewModel 用到的 observeSamplesAfter。 */
internal class FakeSampleDao : SampleDao {
    internal val all = MutableStateFlow<List<Sample>>(emptyList())
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

    override fun observeToday(startOfToday: Long, startOfNextDay: Long): Flow<List<Sample>> = throw NotImplementedError()
    override fun observeAll(): Flow<List<Sample>> = throw NotImplementedError()
    override fun observeByDomain(domainId: Long): Flow<List<Sample>> = throw NotImplementedError()
    override fun observeByErrorType(errorTypeId: Long): Flow<List<Sample>> = throw NotImplementedError()
    override fun observeByAttribution(attribution: String): Flow<List<Sample>> = throw NotImplementedError()
    override fun observeWithNames(domainId: Long, startMillis: Long, endMillis: Long): Flow<List<SampleWithErrorType>> = throw NotImplementedError()
    override fun observeRecentByDomain(domainId: Long, limit: Int): Flow<List<SampleWithErrorType>> = throw NotImplementedError()
    override fun observeTopErrorTypes(domainId: Long, startMillis: Long, endMillis: Long, limit: Int): Flow<List<ErrorTypeCount>> = throw NotImplementedError()
    override fun observeControllableRatio(domainId: Long, startMillis: Long, endMillis: Long): Flow<ControllableRatio?> = throw NotImplementedError()
    override fun observeHighestEmotion(domainId: Long, startMillis: Long, endMillis: Long): Flow<SampleWithErrorType?> = throw NotImplementedError()

    override fun observeSamplesAfter(errorTypeId: Long, startedAt: Long): Flow<List<SampleWithErrorType>> =
        all.map { list ->
            list.filter { it.errorTypeId == errorTypeId && it.recordedAt >= startedAt }
                .sortedByDescending { it.recordedAt }
                .map { s -> SampleWithErrorType(s, errorTypeName = "错误类型${s.errorTypeId}", domainName = "领域${s.domainId}") }
        }

    override suspend fun countByDomain(domainId: Long): Int = all.value.count { it.domainId == domainId }

    override fun observeCount(domainId: Long, startMillis: Long, endMillis: Long): Flow<Int> = throw NotImplementedError()
    override fun observeTopControllableErrorType(domainId: Long, startMillis: Long, endMillis: Long): Flow<ErrorTypeCount?> = throw NotImplementedError()
}

/** 内存假 ErrorTypeDao:实现 getById。 */
internal class FakeErrorTypeDao : ErrorTypeDao {
    private val all = MutableStateFlow<List<ErrorType>>(emptyList())

    fun seed(id: Long, name: String) {
        all.update { it + ErrorType(id = id, name = name, createdAt = 0) }
    }

    override suspend fun insert(errorType: ErrorType): Long {
        all.update { it + errorType }
        return errorType.id
    }

    override fun observeAll(): Flow<List<ErrorType>> = all.asStateFlow()
    override suspend fun getById(id: Long): ErrorType? = all.value.firstOrNull { it.id == id }
    override suspend fun getByName(name: String): ErrorType? = all.value.firstOrNull { it.name == name }
    override suspend fun sampleReferenceCount(id: Long): Int = 0
    override suspend fun trainingReferenceCount(id: Long): Int = 0
    override suspend fun delete(id: Long) {}
}

/** 内存假 DomainDao:实现 getById + observeVisible。 */
internal class FakeDomainDao : DomainDao {
    private val all = MutableStateFlow<List<Domain>>(emptyList())

    fun seed(id: Long, name: String) {
        all.update { it + Domain(id = id, name = name, createdAt = 0, hidden = false) }
    }

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

/** 内存假 SelectedDomainStore。 */
internal class FakeSelectedDomainStore(initial: Long? = null) : SelectedDomainStore {
    private val _state = MutableStateFlow(initial)
    override val flow: Flow<Long?> = _state.asStateFlow()
    override suspend fun set(id: Long?) { _state.value = id }
}
