package com.growthos.app.ui.practice

import com.growthos.app.data.local.LastPracticeDomainStore
import com.growthos.app.data.local.dao.DomainDao
import com.growthos.app.data.local.dao.PracticeSessionDao
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.PracticeSession
import com.growthos.app.data.local.relation.PracticeSessionWithDomainName
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.PracticeSessionRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * [PracticeViewModel] 单测(feature 2026-09-17 / 任务 03)。
 * Fake DAO 喂真 Repository,StandardTestDispatcher 控时序(对齐 WeeklyViewModelTest 范式)。
 * 覆盖:start/finish 状态机、active 引导态、重启恢复、上次领域记忆、按天分组、今日小结。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PracticeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // 真实"今天"做锚点(VM 今日窗口内部用 TimeUtil 本地时区),时钟注入只控制相对偏移
    private val todayStart = com.growthos.app.util.TimeUtil.startOfTodayMillis()
    private var currentNow = todayStart + 10 * 60 * 60 * 1000 // 今天 10:00

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm(
        practiceDao: FakePracticeSessionDao,
        domainDao: FakeDomainDao,
        store: FakeLastPracticeDomainStore
    ): PracticeViewModel = PracticeViewModel(
        practiceRepository = PracticeSessionRepository(practiceDao),
        domainRepository = DomainRepository(domainDao),
        lastPracticeDomainStore = store,
        now = { currentNow }
    )

    @Test
    fun `start creates active row and remembers domain`() = runTest(testDispatcher) {
        val practiceDao = FakePracticeSessionDao()
        val domainDao = FakeDomainDao()
        val store = FakeLastPracticeDomainStore()
        domainDao.insert(Domain(id = 1, name = "编程", createdAt = 0))

        val vm = newVm(practiceDao, domainDao, store)
        advanceUntilIdle()
        vm.start(domainId = 1)
        advanceUntilIdle()

        assertNotNull(vm.active.value)
        assertEquals(1L, vm.active.value?.domainId)
        assertEquals(1L, store.lastId)
    }

    @Test
    fun `finish fills endedAt and clears active`() = runTest(testDispatcher) {
        val practiceDao = FakePracticeSessionDao()
        val domainDao = FakeDomainDao()
        val store = FakeLastPracticeDomainStore()
        domainDao.insert(Domain(id = 1, name = "编程", createdAt = 0))

        val vm = newVm(practiceDao, domainDao, store)
        advanceUntilIdle()
        vm.start(1)
        advanceUntilIdle()
        currentNow += 45 * 60 * 1000 // 45 分钟后
        vm.finish("练了一段")
        advanceUntilIdle()

        assertNull(vm.active.value)
        val row = practiceDao.getById(1)!!
        assertEquals(currentNow, row.endedAt)
        assertEquals("练了一段", row.note)
    }

    @Test
    fun `active survives vm rebuild`() = runTest(testDispatcher) {
        // AC-01:进程被杀重启(VM 重建),activeSession Flow 复现同一行
        val practiceDao = FakePracticeSessionDao()
        val domainDao = FakeDomainDao()
        val store = FakeLastPracticeDomainStore()
        domainDao.insert(Domain(id = 1, name = "编程", createdAt = 0))

        val vm1 = newVm(practiceDao, domainDao, store)
        advanceUntilIdle()
        vm1.start(1)
        advanceUntilIdle()

        val vm2 = newVm(practiceDao, domainDao, store) // 模拟重启
        advanceUntilIdle()
        assertEquals(vm1.active.value?.id, vm2.active.value?.id)
        assertNotNull(vm2.active.value)
    }

    @Test
    fun `day groups bucket by local day and sort descending`() = runTest(testDispatcher) {
        val practiceDao = FakePracticeSessionDao()
        val domainDao = FakeDomainDao()
        val store = FakeLastPracticeDomainStore()
        domainDao.insert(Domain(id = 1, name = "编程", createdAt = 0))
        val day = 86_400_000L
        practiceDao.insert(PracticeSession(domainId = 1, startedAt = todayStart - day, endedAt = todayStart - day + 30 * 60 * 1000, createdAt = 0))
        practiceDao.insert(PracticeSession(domainId = 1, startedAt = todayStart - 2 * day, endedAt = todayStart - 2 * day + 60 * 60 * 1000, createdAt = 0))
        practiceDao.insert(PracticeSession(domainId = 1, startedAt = todayStart, endedAt = todayStart + 20 * 60 * 1000, createdAt = 0))
        practiceDao.insert(PracticeSession(domainId = 1, startedAt = todayStart + 5 * 60 * 1000, endedAt = null, createdAt = 0)) // 进行中不进列表

        val vm = newVm(practiceDao, domainDao, store)
        advanceUntilIdle()

        val groups = vm.dayGroups.value
        assertEquals(3, groups.size)
        assertEquals(todayStart, groups[0].dayStartMillis) // 今天在前
        assertEquals(20 * 60 * 1000L, groups[0].totalMillis)
    }

    @Test
    fun `today total text reflects today only`() = runTest(testDispatcher) {
        val practiceDao = FakePracticeSessionDao()
        val domainDao = FakeDomainDao()
        val store = FakeLastPracticeDomainStore()
        domainDao.insert(Domain(id = 1, name = "编程", createdAt = 0))
        val day = 86_400_000L
        practiceDao.insert(PracticeSession(domainId = 1, startedAt = todayStart, endedAt = todayStart + 45 * 60 * 1000, createdAt = 0))
        practiceDao.insert(PracticeSession(domainId = 1, startedAt = todayStart - day, endedAt = todayStart - day + 90 * 60 * 1000, createdAt = 0))

        val vm = newVm(practiceDao, domainDao, store)
        advanceUntilIdle()

        assertEquals("45分", vm.todayTotalText.value)
    }
}

// ---------- Fakes ----------

private class FakePracticeSessionDao : PracticeSessionDao {
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

    override fun observeActive(): Flow<PracticeSession?> = rows.map { list -> list.firstOrNull { it.endedAt == null } }

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
                domainName = "领域$d",
                totalMillis = items.sumOf { it.endedAt!! - it.startedAt }
            )
        }
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

private class FakeDomainDao : DomainDao {
    private val rows = MutableStateFlow<List<Domain>>(emptyList())

    override suspend fun insert(domain: Domain): Long {
        rows.value = rows.value + domain.copy(id = domain.id.takeIf { it > 0 } ?: (rows.value.size + 1).toLong())
        return 1
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
    override fun observeVisible(): Flow<List<Domain>> = rows.map { list -> list.filterNot { it.hidden } }

    override suspend fun getById(id: Long): Domain? = rows.value.firstOrNull { it.id == id }

    override fun observeById(id: Long): Flow<Domain?> =
        rows.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun setHidden(id: Long, hidden: Boolean) {
        rows.value = rows.value.map { if (it.id == id) it.copy(hidden = hidden) else it }
    }
}

private class FakeLastPracticeDomainStore : LastPracticeDomainStore {
    var lastId: Long? = null
    override val flow: Flow<Long?> = MutableStateFlow(lastId)
    override suspend fun set(id: Long?) {
        lastId = id
        (flow as MutableStateFlow).value = id
    }
}
