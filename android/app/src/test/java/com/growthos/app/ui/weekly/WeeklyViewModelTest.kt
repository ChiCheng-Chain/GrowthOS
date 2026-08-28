package com.growthos.app.ui.weekly

import com.growthos.app.data.local.dao.DomainDao
import com.growthos.app.data.local.dao.SampleDao
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.Sample
import com.growthos.app.data.local.relation.ControllableRatio
import com.growthos.app.data.local.relation.ErrorTypeCount
import com.growthos.app.data.local.relation.SampleWithErrorType
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.SampleRepository
import com.growthos.app.domain.model.Attribution
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [WeeklyViewModel] 状态逻辑单测(二期阶段 4 / 设计 §测试)。
 *
 * 套路同 DomainStatsViewModelTest:内存假 DAO 喂真 Repository,StandardTestDispatcher
 * 控时序,不引 truth/turbine(踩坑 P10)。Fake 做真实时间过滤(对齐 Room 行为),
 * 验证 days/domainFilter 联动重算与五项透传;时间窗口本身的边界由 Room 往返测试覆盖。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WeeklyViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm(sampleDao: FakeSampleDao, domainDao: FakeDomainDao): WeeklyViewModel =
        WeeklyViewModel(
            sampleRepository = SampleRepository(sampleDao),
            domainRepository = DomainRepository(domainDao)
        )

    @Test
    fun `default is 7 days all domains`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        val domainDao = FakeDomainDao()
        // 两条今天样本(全局,跨领域)
        sampleDao.insert(makeSample(domainId = 1, errorTypeId = 10, Attribution.CONTROLLABLE, daysAgo = 0))
        sampleDao.insert(makeSample(domainId = 2, errorTypeId = 20, Attribution.UNCONTROLLABLE, daysAgo = 0))

        val vm = newVm(sampleDao, domainDao)
        advanceUntilIdle()
        val state = vm.uiState.value

        assertEquals(7, state.days)
        assertTrue(state.domainFilter is DomainFilter.All)
        assertEquals(2, state.sampleCount) // F1 跨领域
    }

    @Test
    fun `switching to single domain narrows count`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        val domainDao = FakeDomainDao()
        sampleDao.insert(makeSample(domainId = 1, errorTypeId = 10, Attribution.CONTROLLABLE, daysAgo = 0))
        sampleDao.insert(makeSample(domainId = 1, errorTypeId = 10, Attribution.CONTROLLABLE, daysAgo = 0))
        sampleDao.insert(makeSample(domainId = 2, errorTypeId = 20, Attribution.CONTROLLABLE, daysAgo = 0))

        val vm = newVm(sampleDao, domainDao)
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.sampleCount)

        vm.selectDomain(DomainFilter.Single(1L))
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.sampleCount)
        assertTrue(vm.uiState.value.domainFilter is DomainFilter.Single)
    }

    @Test
    fun `switching days widens time window`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        val domainDao = FakeDomainDao()
        // 一条今天(7/14/30 天都含),一条 10 天前(仅 14/30 天含)
        sampleDao.insert(makeSample(domainId = 1, errorTypeId = 10, Attribution.CONTROLLABLE, daysAgo = 0))
        sampleDao.insert(makeSample(domainId = 1, errorTypeId = 10, Attribution.CONTROLLABLE, daysAgo = 10))

        val vm = newVm(sampleDao, domainDao)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.sampleCount) // 默认 7 天只含今天那条

        vm.selectDays(14)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.sampleCount)
        assertEquals(14, vm.uiState.value.days)

        vm.selectDays(30)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.sampleCount)
    }

    @Test
    fun `top errors returns top three ordered by count`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        seedControllable(sampleDao, domainId = 0, errorTypeId = 10, count = 4)
        seedControllable(sampleDao, domainId = 0, errorTypeId = 20, count = 3)
        seedControllable(sampleDao, domainId = 0, errorTypeId = 30, count = 1)

        val vm = newVm(sampleDao, FakeDomainDao())
        advanceUntilIdle()
        val top = vm.uiState.value.topErrors
        assertEquals(3, top.size)
        assertEquals(10L, top[0].errorTypeId)
        assertEquals(4, top[0].count)
        assertEquals(20L, top[1].errorTypeId)
    }

    @Test
    fun `controllable ratio computes controllable over total`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        // 3 可控 + 1 不可控 = total 4, controllable 3
        seedControllable(sampleDao, domainId = 0, errorTypeId = 10, count = 3)
        sampleDao.insert(makeSample(0, 10, Attribution.UNCONTROLLABLE, daysAgo = 0))

        val vm = newVm(sampleDao, FakeDomainDao())
        advanceUntilIdle()
        val ratio = vm.uiState.value.controllableRatio!!
        assertEquals(4, ratio.total)
        assertEquals(3, ratio.controllable)
    }

    @Test
    fun `controllable ratio null when no samples`() = runTest(testDispatcher) {
        val vm = newVm(FakeSampleDao(), FakeDomainDao())
        advanceUntilIdle()
        // Fake 对空表返回 null(对齐 Room 单行聚合无行时 emit null)
        assertNull(vm.uiState.value.controllableRatio)
    }

    @Test
    fun `highest emotion returns max intensity`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        sampleDao.insert(makeSample(0, 10, Attribution.CONTROLLABLE, daysAgo = 0, emotion = 2))
        sampleDao.insert(makeSample(0, 20, Attribution.CONTROLLABLE, daysAgo = 0, emotion = 5))

        val vm = newVm(sampleDao, FakeDomainDao())
        advanceUntilIdle()
        val emo = vm.uiState.value.highestEmotion!!
        assertEquals(5, emo.sample.emotionIntensity)
        assertEquals(20L, emo.sample.errorTypeId)
    }

    @Test
    fun `suggested error is top controllable error type`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        // 10 号错误 4 次可控(应为建议关注),20 号 2 次可控,30 号 5 次但不可控(不计)
        seedControllable(sampleDao, domainId = 0, errorTypeId = 10, count = 4)
        seedControllable(sampleDao, domainId = 0, errorTypeId = 20, count = 2)
        repeat(5) { sampleDao.insert(makeSample(0, 30, Attribution.UNCONTROLLABLE, daysAgo = 0)) }

        val vm = newVm(sampleDao, FakeDomainDao())
        advanceUntilIdle()
        val suggested = vm.uiState.value.suggestedError!!
        assertEquals(10L, suggested.errorTypeId)
        assertEquals(4, suggested.count)
    }

    @Test
    fun `suggested error null when no controllable`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        // 只有不可控 → 无可控错误 → 建议 null
        sampleDao.insert(makeSample(0, 10, Attribution.UNCONTROLLABLE, daysAgo = 0))

        val vm = newVm(sampleDao, FakeDomainDao())
        advanceUntilIdle()
        assertNull(vm.uiState.value.suggestedError)
    }

    @Test
    fun `available domains come from observeVisible`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        val domainDao = FakeDomainDao()
        domainDao.insert(Domain(id = 1, name = "编程", createdAt = 0, hidden = false))
        domainDao.insert(Domain(id = 2, name = "酒馆战旗", createdAt = 1, hidden = false))
        domainDao.insert(Domain(id = 3, name = "已隐藏", createdAt = 2, hidden = true))

        val vm = newVm(sampleDao, domainDao)
        advanceUntilIdle()
        val domains = vm.uiState.value.availableDomains
        assertEquals(2, domains.size) // 隐藏的不算
        assertFalse(domains.any { it.hidden })
    }

    @Test
    fun `selecting days back to 7 narrows window again`() = runTest(testDispatcher) {
        val sampleDao = FakeSampleDao()
        sampleDao.insert(makeSample(domainId = 1, errorTypeId = 10, Attribution.CONTROLLABLE, daysAgo = 0))
        sampleDao.insert(makeSample(domainId = 1, errorTypeId = 10, Attribution.CONTROLLABLE, daysAgo = 10))

        val vm = newVm(sampleDao, FakeDomainDao())
        advanceUntilIdle()
        vm.selectDays(30)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.sampleCount)

        vm.selectDays(7)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.sampleCount)
    }

    // ---------- 辅助 ----------

    /** 插 count 条可控样本,errorTypeId 固定,daysAgo 控制时间(默认今天)。 */
    private suspend fun seedControllable(dao: FakeSampleDao, domainId: Long, errorTypeId: Long, count: Int) {
        repeat(count) { dao.insert(makeSample(domainId, errorTypeId, Attribution.CONTROLLABLE, daysAgo = 0)) }
    }

    /**
     * 构造样本。daysAgo=0 表示今天,落在所有窗口;daysAgo>0 表示 N 天前,
     * 用真实偏移让 Fake 的时间过滤产生窗口差异。
     */
    private fun makeSample(
        domainId: Long,
        errorTypeId: Long,
        attribution: Attribution,
        daysAgo: Int,
        emotion: Int? = null
    ) = Sample(
        domainId = domainId,
        recordedAt = TimeUtil.startOfTodayMillis() - daysAgo * DAY_MILLIS,
        result = "结果",
        errorTypeId = errorTypeId,
        attribution = attribution,
        emotionIntensity = emotion,
        review = "复盘"
    )

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}

/**
 * 内存假 SampleDao:实现 WeeklyViewModel 调用的五个 observe 方法,做真实时间窗口与
 * domainId 过滤(对齐 Room 行为)。未用到的方法抛 NotImplementedError。
 *
 * domainId=0 表示全局(不过滤领域)。时间窗口 [startMillis, endMillis) 开区间。
 */
private class FakeSampleDao : SampleDao {
    private val all = MutableStateFlow<List<Sample>>(emptyList())
    private var counter = 0L

    // 导入 feature 扩展,Fake 不涉及,空实现。
    override suspend fun insertAll(samples: List<Sample>) {}

    override suspend fun deleteAll() {}

    override suspend fun countAll(): Int = 0

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

    override fun observeAll(): Flow<List<Sample>> = throw NotImplementedError()
    override fun observeByDomain(domainId: Long): Flow<List<Sample>> = throw NotImplementedError()
    override fun observeByErrorType(errorTypeId: Long): Flow<List<Sample>> = throw NotImplementedError()
    override fun observeByAttribution(attribution: String): Flow<List<Sample>> = throw NotImplementedError()

    override fun observeWithNames(domainId: Long, startMillis: Long, endMillis: Long): Flow<List<SampleWithErrorType>> =
        throw NotImplementedError()

    override fun observeRecentByDomain(domainId: Long, limit: Int): Flow<List<SampleWithErrorType>> =
        throw NotImplementedError()

    private fun inWindow(s: Sample, domainId: Long, startMillis: Long, endMillis: Long): Boolean =
        (domainId == 0L || s.domainId == domainId) &&
            s.recordedAt >= startMillis && s.recordedAt < endMillis

    override fun observeTopErrorTypes(
        domainId: Long, startMillis: Long, endMillis: Long, limit: Int
    ): Flow<List<ErrorTypeCount>> = all.map { list ->
        list.filter { inWindow(it, domainId, startMillis, endMillis) }
            .groupBy { it.errorTypeId }
            .map { (eid, samples) -> ErrorTypeCount(eid, "错误类型$eid", samples.size) }
            .sortedByDescending { it.count }
            .take(limit)
    }

    override fun observeControllableRatio(
        domainId: Long, startMillis: Long, endMillis: Long
    ): Flow<ControllableRatio?> = all.map { list ->
        val win = list.filter { inWindow(it, domainId, startMillis, endMillis) }
        if (win.isEmpty()) null
        else ControllableRatio(
            total = win.size,
            controllable = win.count { it.attribution == Attribution.CONTROLLABLE }
        )
    }

    override fun observeHighestEmotion(
        domainId: Long, startMillis: Long, endMillis: Long
    ): Flow<SampleWithErrorType?> = all.map { list ->
        list.filter { inWindow(it, domainId, startMillis, endMillis) && it.emotionIntensity != null }
            .maxByOrNull { it.emotionIntensity!! }
            ?.let { SampleWithErrorType(it, errorTypeName = "错误类型${it.errorTypeId}", domainName = "领域${it.domainId}") }
    }

    override fun observeSamplesAfter(errorTypeId: Long, startedAt: Long): Flow<List<SampleWithErrorType>> =
        throw NotImplementedError()

    override suspend fun countByDomain(domainId: Long): Int =
        all.value.count { it.domainId == domainId }

    override fun observeCount(domainId: Long, startMillis: Long, endMillis: Long): Flow<Int> =
        all.map { list -> list.count { inWindow(it, domainId, startMillis, endMillis) } }

    override fun observeTopControllableErrorType(
        domainId: Long, startMillis: Long, endMillis: Long
    ): Flow<ErrorTypeCount?> = all.map { list ->
        list.filter { inWindow(it, domainId, startMillis, endMillis) && it.attribution == Attribution.CONTROLLABLE }
            .groupBy { it.errorTypeId }
            .map { (eid, samples) -> ErrorTypeCount(eid, "错误类型$eid", samples.size) }
            .maxByOrNull { it.count }
    }
}

/** 内存假 DomainDao:只实现 insert + observeVisible。 */
private class FakeDomainDao : DomainDao {
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
        all.map { list -> list.filter { !it.hidden }.sortedBy { it.createdAt } }

    override suspend fun getById(id: Long): Domain? = all.value.firstOrNull { it.id == id }
    override fun observeById(id: Long): Flow<Domain?> = all.map { it.firstOrNull { d -> d.id == id } }
    override suspend fun setHidden(id: Long, hidden: Boolean) {
        all.update { list -> list.map { if (it.id == id) it.copy(hidden = hidden) else it } }
    }
}
