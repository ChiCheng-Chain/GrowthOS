package com.growthos.app.ui.practice

import com.growthos.app.data.local.dao.DomainDao
import com.growthos.app.data.local.dao.PracticeSessionDao
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.PracticeSession
import com.growthos.app.data.local.relation.PracticeSessionWithDomainName
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.PracticeSessionRepository
import com.growthos.app.ui.weekly.DomainFilter
import com.growthos.app.util.TimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * [PracticeBoardViewModel] 单测(feature 2026-09-17 / 任务 04 / AC-09)。
 * 混合成败…不,混合领域数据,断言三区块统计精确到值;口径切换与按天分桶时区边界。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PracticeBoardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val todayStart = TimeUtil.startOfTodayMillis()
    private val day = 86_400_000L

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm(practiceDao: FakeBoardPracticeDao, domainDao: FakeBoardDomainDao) =
        PracticeBoardViewModel(
            practiceRepository = PracticeSessionRepository(practiceDao),
            domainRepository = DomainRepository(domainDao)
        )

    private suspend fun seedBoard(practiceDao: FakeBoardPracticeDao, domainDao: FakeBoardDomainDao) {
        domainDao.insert(Domain(id = 1, name = "编程", createdAt = 0))
        domainDao.insert(Domain(id = 2, name = "吉他", createdAt = 0))
        practiceDao.domainNames[1L] = "编程"
        practiceDao.domainNames[2L] = "吉他"
        val h = 60 * 60 * 1000L
        // 默认 7 天窗口:编程 2h+1h,吉他 30min;窗口外(8 天前)1h 不计
        practiceDao.insert(PracticeSession(domainId = 1, startedAt = todayStart - day, endedAt = todayStart - day + 2 * h, createdAt = 0))
        practiceDao.insert(PracticeSession(domainId = 1, startedAt = todayStart - 2 * day, endedAt = todayStart - 2 * day + h, createdAt = 0))
        practiceDao.insert(PracticeSession(domainId = 2, startedAt = todayStart - 3 * day, endedAt = todayStart - 3 * day + 30 * 60 * 1000, createdAt = 0))
        practiceDao.insert(PracticeSession(domainId = 1, startedAt = todayStart - 8 * day, endedAt = todayStart - 8 * day + h, createdAt = 0))
        // 进行中行不计入
        practiceDao.insert(PracticeSession(domainId = 1, startedAt = todayStart, endedAt = null, createdAt = 0))
    }

    @Test
    fun `default board shows total average domain and daily buckets`() = runTest(testDispatcher) {
        val practiceDao = FakeBoardPracticeDao()
        val domainDao = FakeBoardDomainDao()
        seedBoard(practiceDao, domainDao)

        val vm = newVm(practiceDao, domainDao)
        advanceUntilIdle()
        val s = vm.uiState.value

        assertEquals(7, s.days)
        assertEquals(3, s.count)
        assertEquals("3小时30分", s.totalText)
        assertEquals("1小时10分", s.averageText)
        assertEquals(2, s.domainTotals.size)
        assertEquals("编程", s.domainTotals[0].domainName)
        assertEquals(3, s.dailyTotals.size) // -1/-2/-3 天各一桶,-8 天在窗外
    }

    @Test
    fun `switching days widens window`() = runTest(testDispatcher) {
        val practiceDao = FakeBoardPracticeDao()
        val domainDao = FakeBoardDomainDao()
        seedBoard(practiceDao, domainDao)

        val vm = newVm(practiceDao, domainDao)
        advanceUntilIdle()
        vm.selectDays(30)
        advanceUntilIdle()
        val s = vm.uiState.value

        assertEquals(30, s.days)
        assertEquals(4, s.count) // 窗口外那条进入
        assertEquals("4小时30分", s.totalText)
    }

    @Test
    fun `single domain filter narrows stats`() = runTest(testDispatcher) {
        val practiceDao = FakeBoardPracticeDao()
        val domainDao = FakeBoardDomainDao()
        seedBoard(practiceDao, domainDao)

        val vm = newVm(practiceDao, domainDao)
        advanceUntilIdle()
        vm.selectDomain(DomainFilter.Single(2))
        advanceUntilIdle()
        val s = vm.uiState.value

        assertEquals(1, s.count)
        assertEquals("30分", s.totalText)
        assertEquals(1, s.domainTotals.size)
        assertEquals("吉他", s.domainTotals[0].domainName)
    }

    @Test
    fun `daily buckets align to local midnight`() = runTest(testDispatcher) {
        // 分桶时区边界:startedAt 在当日 23:50-结束跨 0:10,归 startedAt 当天(练习归属起点日)
        val practiceDao = FakeBoardPracticeDao()
        val domainDao = FakeBoardDomainDao()
        domainDao.insert(Domain(id = 1, name = "编程", createdAt = 0))
        val lateNight = todayStart + 23 * 60 * 60 * 1000 + 50 * 60 * 1000
        practiceDao.insert(PracticeSession(domainId = 1, startedAt = lateNight, endedAt = lateNight + 20 * 60 * 1000, createdAt = 0))

        val vm = newVm(practiceDao, domainDao)
        advanceUntilIdle()
        val s = vm.uiState.value

        assertEquals(1, s.dailyTotals.size)
        assertEquals(todayStart, s.dailyTotals[0].dayStartMillis)
        assertEquals(20 * 60 * 1000L, s.dailyTotals[0].totalMillis)
    }
}

// ---------- Fakes(与 PracticeViewModelTest 同构;单独类避免跨文件状态耦合) ----------

private class FakeBoardPracticeDao(val domainNames: MutableMap<Long, String> = mutableMapOf()) : PracticeSessionDao {
    private val rows = MutableStateFlow<List<PracticeSession>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(session: PracticeSession): Long {
        val id = session.id.takeIf { it > 0 } ?: nextId++
        rows.value = rows.value + session.copy(id = id)
        return id
    }

    override suspend fun insertAll(sessions: List<PracticeSession>) {
        sessions.forEach { insert(it) }
    }

    override suspend fun update(session: PracticeSession) {
        rows.value = rows.value.map { if (it.id == session.id) session else it }
    }

    override suspend fun deleteById(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }

    override suspend fun countAll(): Int = rows.value.size

    override suspend fun getById(id: Long): PracticeSession? = rows.value.firstOrNull { it.id == id }

    override suspend fun getActive(): PracticeSession? = rows.value.firstOrNull { it.endedAt == null }

    override fun observeActive(): Flow<PracticeSession?> = rows.map { it.firstOrNull { s -> s.endedAt == null } }

    override suspend fun getAll(): List<PracticeSession> = rows.value.sortedByDescending { it.startedAt }

    override suspend fun countActiveOverlapping(endMillis: Long, excludeId: Long): Int =
        rows.value.count { it.endedAt == null && it.id != excludeId && it.startedAt < endMillis }

    override fun observeAllWithDomain(): Flow<List<PracticeSessionWithDomainName>> =
        rows.map { list -> list.map { PracticeSessionWithDomainName(it, "领域${it.domainId}") } }

    override fun observeTotalAndCount(
        domainId: Long,
        startMillis: Long,
        endMillis: Long
    ): Flow<com.growthos.app.data.local.dao.PracticeTotalAndCount> = rows.map { list ->
        val inWindow = list.filter {
            it.endedAt != null && (domainId == 0L || it.domainId == domainId) &&
                it.startedAt >= startMillis && it.startedAt < endMillis
        }
        com.growthos.app.data.local.dao.PracticeTotalAndCount(
            totalMillis = inWindow.sumOf { it.endedAt!! - it.startedAt },
            count = inWindow.size
        )
    }

    override fun observeByDomain(
        domainId: Long,
        startMillis: Long,
        endMillis: Long
    ): Flow<List<com.growthos.app.data.local.dao.PracticeDomainTotal>> = rows.map { list ->
        list.filter {
            it.endedAt != null && (domainId == 0L || it.domainId == domainId) &&
                it.startedAt >= startMillis && it.startedAt < endMillis
        }.groupBy { it.domainId }.map { (d, items) ->
            com.growthos.app.data.local.dao.PracticeDomainTotal(
                domainId = d,
                domainName = domainNames[d] ?: "领域$d",
                totalMillis = items.sumOf { it.endedAt!! - it.startedAt }
            )
        }.sortedByDescending { it.totalMillis }
    }

    override fun observeWindowDetails(
        domainId: Long,
        startMillis: Long,
        endMillis: Long
    ): Flow<List<com.growthos.app.data.local.dao.PracticeDetail>> = rows.map { list ->
        list.filter {
            it.endedAt != null && (domainId == 0L || it.domainId == domainId) &&
                it.startedAt >= startMillis && it.startedAt < endMillis
        }.map { com.growthos.app.data.local.dao.PracticeDetail(it.startedAt, it.endedAt!!, it.domainId) }
    }
}

private class FakeBoardDomainDao : DomainDao {
    private val rows = MutableStateFlow<List<Domain>>(emptyList())

    override suspend fun insert(domain: Domain): Long {
        rows.value = rows.value + domain
        return domain.id
    }

    override suspend fun insertAll(domains: List<Domain>) {
        domains.forEach { insert(it) }
    }

    override suspend fun update(domain: Domain) {
        rows.value = rows.value.map { if (it.id == domain.id) domain else it }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }

    override suspend fun countAll(): Int = rows.value.size

    override fun observeAll(): Flow<List<Domain>> = rows
    override fun observeVisible(): Flow<List<Domain>> = rows.map { it.filterNot { d -> d.hidden } }

    override suspend fun getById(id: Long): Domain? = rows.value.firstOrNull { it.id == id }

    override fun observeById(id: Long): Flow<Domain?> = rows.map { it.firstOrNull { d -> d.id == id } }

    override suspend fun setHidden(id: Long, hidden: Boolean) {
        rows.value = rows.value.map { if (it.id == id) it.copy(hidden = hidden) else it }
    }
}
