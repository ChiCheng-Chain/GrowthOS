package com.growthos.app.ui.record

import com.growthos.app.data.local.SelectedDomainStore
import com.growthos.app.data.local.dao.DomainDao
import com.growthos.app.data.local.dao.ErrorTypeDao
import com.growthos.app.data.local.dao.SampleDao
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.data.local.entity.Sample
import com.growthos.app.data.local.relation.ControllableRatio
import com.growthos.app.data.local.relation.ErrorTypeCount
import com.growthos.app.data.local.relation.SampleWithErrorType
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.ErrorTypeRepository
import com.growthos.app.data.repository.ErrorTypeRepositoryImpl
import com.growthos.app.data.repository.SampleRepository
import com.growthos.app.domain.model.Attribution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [SampleViewModel] 状态逻辑单测(二期阶段 2 / 设计文档 §测试)。
 *
 * 套路同 [com.growthos.app.ui.domain.DomainViewModelTest]:
 * 内存假 DAO/Store 喂真 Repository,StandardTestDispatcher 控时序,
 * 不引 truth/turbine(踩坑 P10),持久化类抽接口注入内存实现(踩坑 P4.6)。
 * 时钟 `now` 注入固定值,验证新建 recordedAt。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SampleViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedNow = 1_700_000_000_000L

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm(
        sampleDao: FakeSampleDao,
        errorTypeDao: FakeErrorTypeDao,
        domainDao: FakeDomainDao,
        store: FakeSelectedDomainStore,
        sampleId: Long? = null
    ): SampleViewModel = SampleViewModel(
        sampleRepository = SampleRepository(sampleDao),
        errorTypeRepository = ErrorTypeRepositoryImpl(errorTypeDao),
        domainRepository = DomainRepository(domainDao),
        selectedStore = store,
        sampleId = sampleId,
        now = { fixedNow }
    )

    private fun seedDomains(dao: FakeDomainDao, vararg names: String) {
        names.forEachIndexed { idx, name ->
            val id = dao.nextId()
            dao.upsert(Domain(id = id, name = name, createdAt = id.toLong()))
        }
    }

    private fun seedErrorTypes(dao: FakeErrorTypeDao, vararg names: String) {
        names.forEach { name ->
            val id = dao.nextId()
            dao.upsert(ErrorType(id = id, name = name, createdAt = id.toLong()))
        }
    }

    @Test
    fun `form invalid when required fields missing`() = runTest(testDispatcher) {
        val vm = newVm(FakeSampleDao(), FakeErrorTypeDao(), FakeDomainDao(), FakeSelectedDomainStore())
        advanceUntilIdle()
        assertFalse(vm.uiState.value.form.isValid)

        vm.updateResult("结果")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.form.isValid) // 仍缺描述/错误类型/归因/复盘/领域
    }

    @Test
    fun `form valid when all required fields filled`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        val errorTypeDao = FakeErrorTypeDao()
        val domainDao = FakeDomainDao()
        seedDomains(domainDao, "编程")
        seedErrorTypes(errorTypeDao, "边界条件遗漏")
        val vm = newVm(sampleDao, errorTypeDao, domainDao, FakeSelectedDomainStore())
        advanceUntilIdle()

        // 领域默认选中(见下一用例),这里显式补全其余
        vm.updateDomain(1L)
        vm.updateResult("线上 bug")
        vm.updateDescription("退款分支没处理")
        vm.updateErrorType(1L)
        vm.updateAttribution(Attribution.CONTROLLABLE)
        vm.updateReview("下次先列状态表")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.form.isValid)
    }

    @Test
    fun `new sample defaults domain to selected store value`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        val errorTypeDao = FakeErrorTypeDao()
        val domainDao = FakeDomainDao()
        seedDomains(domainDao, "编程", "酒馆战旗")
        val store = FakeSelectedDomainStore(initial = 2L) // 当前选中"酒馆战旗"

        val vm = newVm(sampleDao, errorTypeDao, domainDao, store, sampleId = null)
        advanceUntilIdle()
        assertEquals(2L, vm.uiState.value.form.domainId) // 默认选中当前领域
    }

    @Test
    fun `save inserts new sample with recordedAt = now`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        val errorTypeDao = FakeErrorTypeDao()
        val domainDao = FakeDomainDao()
        seedDomains(domainDao, "编程")
        seedErrorTypes(errorTypeDao, "边界条件遗漏")
        val vm = newVm(sampleDao, errorTypeDao, domainDao, FakeSelectedDomainStore())
        advanceUntilIdle()

        fillValidForm(vm)
        vm.save()
        advanceUntilIdle()

        assertEquals(1, sampleDao.all.value.size)
        val saved = sampleDao.all.value.first()
        assertEquals(fixedNow, saved.recordedAt)
        assertEquals("编程领域 id", 1L, saved.domainId)
        assertEquals(Attribution.CONTROLLABLE, saved.attribution)
        assertEquals(4, saved.emotionIntensity)
    }

    @Test
    fun `edit prefills form from existing sample`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        val errorTypeDao = FakeErrorTypeDao()
        val domainDao = FakeDomainDao()
        seedDomains(domainDao, "编程")
        seedErrorTypes(errorTypeDao, "边界条件遗漏")
        val existingId = sampleDao.insert(
            Sample(
                domainId = 1L, recordedAt = 1000L, result = "旧结果", description = "旧描述",
                errorTypeId = 1L, attribution = Attribution.UNCONTROLLABLE,
                emotionIntensity = null, review = "旧复盘"
            )
        )
        advanceUntilIdle()

        val vm = newVm(sampleDao, errorTypeDao, domainDao, FakeSelectedDomainStore(), sampleId = existingId)
        advanceUntilIdle()
        val form = vm.uiState.value.form
        assertTrue(vm.uiState.value.isEditing)
        assertEquals("旧结果", form.result)
        assertEquals(Attribution.UNCONTROLLABLE, form.attribution)
        assertNull(form.emotionIntensity)
    }

    @Test
    fun `edit save preserves original recordedAt`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        val errorTypeDao = FakeErrorTypeDao()
        val domainDao = FakeDomainDao()
        seedDomains(domainDao, "编程")
        seedErrorTypes(errorTypeDao, "边界条件遗漏")
        val originalTime = 1000L
        val existingId = sampleDao.insert(
            Sample(
                domainId = 1L, recordedAt = originalTime, result = "旧结果", description = "旧描述",
                errorTypeId = 1L, attribution = Attribution.UNCONTROLLABLE,
                emotionIntensity = null, review = "旧复盘"
            )
        )
        advanceUntilIdle()

        val vm = newVm(sampleDao, errorTypeDao, domainDao, FakeSelectedDomainStore(), sampleId = existingId)
        advanceUntilIdle()
        vm.updateResult("新结果")
        vm.updateReview("新复盘")
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        val updated = sampleDao.all.value.first { it.id == existingId }
        assertEquals(originalTime, updated.recordedAt) // 保留原值,不被 fixedNow 覆盖
        assertEquals("新结果", updated.result)
    }

    @Test
    fun `createErrorType creates and immediately selects new type`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        val errorTypeDao = FakeErrorTypeDao()
        val domainDao = FakeDomainDao()
        seedDomains(domainDao, "编程")
        val vm = newVm(sampleDao, errorTypeDao, domainDao, FakeSelectedDomainStore())
        advanceUntilIdle()

        vm.createErrorType("接杀前没回位")
        advanceUntilIdle()

        assertEquals(1L, vm.uiState.value.form.errorTypeId) // 立即选中
        assertTrue(vm.uiState.value.errorTypes.any { it.name == "接杀前没回位" })
    }

    @Test
    fun `createErrorType reuses existing on duplicate name`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        val errorTypeDao = FakeErrorTypeDao()
        val domainDao = FakeDomainDao()
        seedDomains(domainDao, "编程")
        seedErrorTypes(errorTypeDao, "边界条件遗漏") // id=1 已存在
        val vm = newVm(sampleDao, errorTypeDao, domainDao, FakeSelectedDomainStore())
        advanceUntilIdle()

        vm.createErrorType("边界条件遗漏") // 重名
        advanceUntilIdle()

        assertEquals(1L, vm.uiState.value.form.errorTypeId) // 复用已有 id
        assertEquals(1, vm.uiState.value.errorTypes.size) // 没新增
    }

    @Test
    fun `emotion toggle clears when same value tapped`() = runTest(testDispatcher) {
        val vm = newVm(FakeSampleDao(), FakeErrorTypeDao(), FakeDomainDao(), FakeSelectedDomainStore())
        advanceUntilIdle()
        vm.updateEmotion(4)
        advanceUntilIdle()
        assertEquals(4, vm.uiState.value.form.emotionIntensity)
        vm.updateEmotion(4) // 再点已选档
        advanceUntilIdle()
        assertNull(vm.uiState.value.form.emotionIntensity)
    }

    @Test
    fun `delete removes existing sample`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        val errorTypeDao = FakeErrorTypeDao()
        val domainDao = FakeDomainDao()
        seedDomains(domainDao, "编程")
        seedErrorTypes(errorTypeDao, "边界条件遗漏")
        val existingId = sampleDao.insert(
            Sample(
                domainId = 1L, recordedAt = 1000L, result = "结果", description = "描述",
                errorTypeId = 1L, attribution = Attribution.CONTROLLABLE,
                emotionIntensity = null, review = "复盘"
            )
        )
        advanceUntilIdle()

        val vm = newVm(sampleDao, errorTypeDao, domainDao, FakeSelectedDomainStore(), sampleId = existingId)
        advanceUntilIdle()
        vm.delete()
        advanceUntilIdle()

        assertNull(sampleDao.all.value.firstOrNull { it.id == existingId })
    }

    // ---------- 阶段 7:错误类型删除(R-014) ----------

    @Test
    fun `requestDeleteErrorType emits Blocked when referenced`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        // errorType id=1 被 3 条样本引用
        val errorTypeDao = FakeErrorTypeDao(sampleRefCount = { id -> if (id == 1L) 3 else 0 })
        val domainDao = FakeDomainDao()
        seedDomains(domainDao, "编程")
        seedErrorTypes(errorTypeDao, "边界条件遗漏")
        val vm = newVm(sampleDao, errorTypeDao, domainDao, FakeSelectedDomainStore())
        advanceUntilIdle()

        val collected = mutableListOf<ErrorTypeDeleteEvent>()
        val job = launch { vm.errorTypeDeleteEvents.toList(collected) }
        advanceUntilIdle()

        val et = vm.uiState.value.errorTypes.first { it.name == "边界条件遗漏" }
        vm.requestDeleteErrorType(et)
        advanceUntilIdle()
        job.cancel()

        val event = collected.firstOrNull()
        assertTrue("应 emit Blocked,实际 $event", event is ErrorTypeDeleteEvent.Blocked)
        assertEquals(3, (event as ErrorTypeDeleteEvent.Blocked).referenceCount)
        // 未删
        assertTrue(vm.uiState.value.errorTypes.any { it.name == "边界条件遗漏" })
    }

    @Test
    fun `requestDeleteErrorType emits ConfirmDelete when not referenced`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        val errorTypeDao = FakeErrorTypeDao() // 默认 0 引用
        val domainDao = FakeDomainDao()
        seedDomains(domainDao, "编程")
        seedErrorTypes(errorTypeDao, "边界条件遗漏")
        val vm = newVm(sampleDao, errorTypeDao, domainDao, FakeSelectedDomainStore())
        advanceUntilIdle()

        val collected = mutableListOf<ErrorTypeDeleteEvent>()
        val job = launch { vm.errorTypeDeleteEvents.toList(collected) }
        advanceUntilIdle()

        val et = vm.uiState.value.errorTypes.first { it.name == "边界条件遗漏" }
        vm.requestDeleteErrorType(et)
        advanceUntilIdle()
        job.cancel()

        assertTrue(collected.firstOrNull() is ErrorTypeDeleteEvent.ConfirmDelete)
        // 未真正删(等 confirm)
        assertTrue(vm.uiState.value.errorTypes.any { it.name == "边界条件遗漏" })
    }

    @Test
    fun `confirmDeleteErrorType removes type and clears selection`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        val errorTypeDao = FakeErrorTypeDao()
        val domainDao = FakeDomainDao()
        seedDomains(domainDao, "编程")
        seedErrorTypes(errorTypeDao, "边界条件遗漏")
        val vm = newVm(sampleDao, errorTypeDao, domainDao, FakeSelectedDomainStore())
        advanceUntilIdle()

        val et = vm.uiState.value.errorTypes.first { it.name == "边界条件遗漏" }
        vm.updateErrorType(et.id) // 选中它
        advanceUntilIdle()
        assertEquals(et.id, vm.uiState.value.form.errorTypeId)

        vm.confirmDeleteErrorType(et)
        advanceUntilIdle()

        assertTrue("应已删除", vm.uiState.value.errorTypes.none { it.id == et.id })
        assertNull("选中应清空", vm.uiState.value.form.errorTypeId)
    }

    @Test
    fun `save emits Saved event`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        val errorTypeDao = FakeErrorTypeDao()
        val domainDao = FakeDomainDao()
        seedDomains(domainDao, "编程")
        seedErrorTypes(errorTypeDao, "边界条件遗漏")
        val vm = newVm(sampleDao, errorTypeDao, domainDao, FakeSelectedDomainStore())
        advanceUntilIdle()

        // SharedFlow(replay=0)无订阅者时 tryEmit 会丢弃,故先 launch 订阅再 save。
        val collected = mutableListOf<SampleEvent>()
        val job = launch { vm.events.toList(collected) }
        fillValidForm(vm)
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        job.cancel()

        assertTrue(collected.contains(SampleEvent.Saved))
    }

    private fun fillValidForm(vm: SampleViewModel) {
        vm.updateDomain(1L)
        vm.updateResult("线上 bug")
        vm.updateDescription("退款分支没处理")
        vm.updateErrorType(1L)
        vm.updateAttribution(Attribution.CONTROLLABLE)
        vm.updateEmotion(4)
        vm.updateReview("下次先列状态表")
    }
}

/** 内存假 Store:同 DomainViewModelTest。 */
private class FakeSelectedDomainStore(initial: Long? = null) : SelectedDomainStore {
    private val _state = MutableStateFlow(initial)
    override val flow: Flow<Long?> = _state.asStateFlow()
    override suspend fun set(id: Long?) { _state.value = id }
}

/**
 * 内存假 SampleDao:只实现录入页用到的 insert/update/delete/getById/observeById/observeToday,
 * 聚合查询抛 NotImplementedError(测试不调)。observeToday 基于 recordedAt 落在今日窗口。
 */
private class FakeSampleDao : SampleDao {
    val all = MutableStateFlow<List<Sample>>(emptyList())
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
    override fun observeById(id: Long): Flow<Sample?> = all.map { list -> list.firstOrNull { it.id == id } }

    override fun observeToday(startOfToday: Long, startOfNextDay: Long): Flow<List<Sample>> =
        all.map { list ->
            list.filter { it.recordedAt >= startOfToday && it.recordedAt < startOfNextDay }
                .sortedByDescending { it.recordedAt }
        }

    // 以下聚合查询录入页不用,测试不调,留空实现。
    override fun observeAll(): Flow<List<Sample>> = all.map { it.sortedByDescending { s -> s.recordedAt } }
    override fun observeByDomain(domainId: Long): Flow<List<Sample>> =
        all.map { it.filter { s -> s.domainId == domainId }.sortedByDescending { s -> s.recordedAt } }
    override fun observeByErrorType(errorTypeId: Long): Flow<List<Sample>> =
        all.map { it.filter { s -> s.errorTypeId == errorTypeId }.sortedByDescending { s -> s.recordedAt } }
    override fun observeByAttribution(attribution: String): Flow<List<Sample>> =
        all.map { it.filter { s -> s.attribution.name == attribution }.sortedByDescending { s -> s.recordedAt } }
    override fun observeWithNames(domainId: Long, startMillis: Long, endMillis: Long): Flow<List<SampleWithErrorType>> =
        throw NotImplementedError()
    override fun observeRecentByDomain(domainId: Long, limit: Int): Flow<List<SampleWithErrorType>> =
        throw NotImplementedError()
    override fun observeTopErrorTypes(domainId: Long, startMillis: Long, endMillis: Long, limit: Int): Flow<List<ErrorTypeCount>> =
        throw NotImplementedError()
    override fun observeControllableRatio(domainId: Long, startMillis: Long, endMillis: Long): Flow<ControllableRatio?> =
        throw NotImplementedError()
    override fun observeHighestEmotion(domainId: Long, startMillis: Long, endMillis: Long): Flow<SampleWithErrorType?> =
        throw NotImplementedError()
    override fun observeSamplesAfter(errorTypeId: Long, startedAt: Long): Flow<List<SampleWithErrorType>> =
        throw NotImplementedError()
    override suspend fun countByDomain(domainId: Long): Int =
        all.value.count { it.domainId == domainId }

    // 阶段 4 周复盘新增查询,录入页不调用 → 抛 NotImplementedError 占位。
    override fun observeCount(domainId: Long, startMillis: Long, endMillis: Long): Flow<Int> =
        throw NotImplementedError()
    override fun observeTopControllableErrorType(
        domainId: Long, startMillis: Long, endMillis: Long
    ): Flow<ErrorTypeCount?> = throw NotImplementedError()
}

/**
 * 内存假 ErrorTypeDao:实现 getOrCreate / 删除 / 引用计数。
 *
 * 阶段 7:[sampleReferenceCount] / [trainingReferenceCount] 改为基于传入的计数 lambda,
 * 让 requestDeleteErrorType 的引用检查可在测试里驱动(默认 0 = 未引用)。
 */
private class FakeErrorTypeDao(
    private val sampleRefCount: (Long) -> Int = { 0 },
    private val trainingRefCount: (Long) -> Int = { 0 }
) : ErrorTypeDao {
    private val all = MutableStateFlow<List<ErrorType>>(emptyList())
    private var counter = 0L

    fun nextId(): Long { counter += 1; return counter }
    fun upsert(et: ErrorType) {
        all.update { list ->
            val idx = list.indexOfFirst { it.id == et.id }
            if (idx >= 0) list.toMutableList().apply { set(idx, et) } else list + et
        }
    }

    override suspend fun insert(errorType: ErrorType): Long {
        // 模拟真 DAO 的 INSERT OR IGNORE + 唯一索引:重名返回 -1。
        if (all.value.any { it.name == errorType.name }) return -1L
        val id = if (errorType.id == 0L) nextId() else errorType.id
        upsert(errorType.copy(id = id))
        return id
    }

    override fun observeAll(): Flow<List<ErrorType>> = all.map { it.sortedBy { e -> e.createdAt } }
    override suspend fun getById(id: Long): ErrorType? = all.value.firstOrNull { it.id == id }
    override suspend fun getByName(name: String): ErrorType? = all.value.firstOrNull { it.name == name }
    override suspend fun update(errorType: ErrorType) = upsert(errorType)
    override suspend fun sampleReferenceCount(id: Long): Int = sampleRefCount(id)
    override suspend fun trainingReferenceCount(id: Long): Int = trainingRefCount(id)
    override suspend fun reassignSamples(fromId: Long, toId: Long) {}
    override suspend fun reassignTrainings(fromId: Long, toId: Long) {}
    override suspend fun delete(id: Long) {
        all.update { list -> list.filterNot { it.id == id } }
    }
}

/** 内存假 DomainDao:复刻 DomainViewModelTest 的实现。 */
private class FakeDomainDao : DomainDao {
    private val all = MutableStateFlow<List<Domain>>(emptyList())
    private var counter = 0L

    fun nextId(): Long { counter += 1; return counter }
    fun upsert(domain: Domain) {
        all.update { list ->
            val idx = list.indexOfFirst { it.id == domain.id }
            if (idx >= 0) list.toMutableList().apply { set(idx, domain) } else list + domain
        }
    }

    override suspend fun insert(domain: Domain): Long {
        val id = if (domain.id == 0L) nextId() else domain.id
        upsert(domain.copy(id = id))
        return id
    }

    override suspend fun update(domain: Domain) = upsert(domain)
    override fun observeAll(): Flow<List<Domain>> = all.map { it.sortedBy { d -> d.createdAt } }
    override fun observeVisible(): Flow<List<Domain>> =
        all.map { it.filter { d -> !d.hidden }.sortedBy { d -> d.createdAt } }
    override suspend fun getById(id: Long): Domain? = all.value.firstOrNull { it.id == id }
    override fun observeById(id: Long): Flow<Domain?> = all.map { it.firstOrNull { d -> d.id == id } }
    override suspend fun setHidden(id: Long, hidden: Boolean) {
        all.update { list -> list.map { if (it.id == id) it.copy(hidden = hidden) else it } }
    }
}
