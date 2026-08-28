package com.growthos.app.ui.domain

import com.growthos.app.data.local.SelectedDomainStore
import com.growthos.app.data.local.dao.DomainDao
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.repository.DomainRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
 * [DomainViewModel] 状态逻辑单测(二期阶段 1 / 设计文档 §测试)。
 *
 * 用内存 [FakeDomainDao] 喂真 [DomainRepository],用 [FakeSelectedDomainStore] 喂 ViewModel——
 * 真持久化往返由 [com.growthos.app.data.local.SelectedDomainStoreTest] 覆盖,这里只测状态逻辑,
 * 故 store 用内存实现,避免 DataStore 的 IO 线程不被测试 dispatcher 控制的时序问题。
 * 不引 turbine / truth(踩坑 P10),用 StateFlow.value + advanceUntilIdle 直接断言。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DomainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm(dao: FakeDomainDao, store: FakeSelectedDomainStore): DomainViewModel {
        val repo = DomainRepository(dao)
        return DomainViewModel(repo, store)
    }

    private suspend fun seed(dao: FakeDomainDao, pairs: List<Pair<String, Boolean>>) {
        pairs.forEach { (name, hidden) ->
            val id = dao.nextId()
            dao.upsert(Domain(id = id, name = name, createdAt = id, hidden = hidden))
        }
    }

    @Test
    fun `selected falls back to first visible when persistent id not in visible list`() = runTest(testDispatcher) {
        val dao = FakeDomainDao()
        seed(dao, listOf("编程" to false, "羽毛球" to false))
        val store = FakeSelectedDomainStore(initial = 999L) // 持久了一个不存在的 id

        val vm = newVm(dao, store)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(1L, state.selectedId)
        assertEquals("编程", state.selectedDomain?.name)
    }

    @Test
    fun `selected null when no visible domains`() = runTest(testDispatcher) {
        val dao = FakeDomainDao()
        seed(dao, listOf("编程" to true, "羽毛球" to true)) // 全隐藏
        val store = FakeSelectedDomainStore()

        val vm = newVm(dao, store)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertNull(state.selectedId)
        assertTrue(state.isEmpty)
        assertEquals(2, state.hiddenDomains.size)
    }

    @Test
    fun `select writes to store and drives state`() = runTest(testDispatcher) {
        val dao = FakeDomainDao()
        seed(dao, listOf("编程" to false, "羽毛球" to false))
        val store = FakeSelectedDomainStore()

        val vm = newVm(dao, store)
        advanceUntilIdle()
        assertEquals(1L, vm.uiState.value.selectedId) // 默认第一个
        vm.select(2L)
        advanceUntilIdle()
        assertEquals(2L, vm.uiState.value.selectedId)
        assertEquals(2L, store.current) // 持久化生效
    }

    @Test
    fun `hiding selected domain falls back to first visible`() = runTest(testDispatcher) {
        val dao = FakeDomainDao()
        seed(dao, listOf("编程" to false, "羽毛球" to false))
        val store = FakeSelectedDomainStore(initial = 1L) // 选中"编程"

        val vm = newVm(dao, store)
        advanceUntilIdle()
        assertEquals(1L, vm.uiState.value.selectedId)
        vm.setHidden(1L, hidden = true) // 隐藏当前选中
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(2L, state.selectedId) // 回退到"羽毛球"
        assertEquals(1, state.domains.size) // chips 只剩一个
        assertEquals(1, state.hiddenDomains.size)
    }

    @Test
    fun `create adds domain and auto-selects it`() = runTest(testDispatcher) {
        val dao = FakeDomainDao()
        val store = FakeSelectedDomainStore()

        val vm = newVm(dao, store)
        advanceUntilIdle()
        assertNull(vm.uiState.value.selectedId) // 空
        vm.create("编程")
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(1, state.domains.size)
        assertEquals("编程", state.domains.first().name)
        assertEquals(1L, state.selectedId) // 自动选中新建
        assertNull(state.dialog) // 对话框已关
    }

    @Test
    fun `openCreate and openEdit toggle dialog state`() = runTest(testDispatcher) {
        val dao = FakeDomainDao()
        seed(dao, listOf("编程" to false))
        val store = FakeSelectedDomainStore()

        val vm = newVm(dao, store)
        advanceUntilIdle()
        assertNull(vm.uiState.value.dialog)
        vm.openCreate()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.dialog is DomainDialog.Create)
        vm.dismissDialog()
        advanceUntilIdle()
        assertNull(vm.uiState.value.dialog)

        val target = dao.observeVisible().first().first()
        vm.openEdit(target)
        advanceUntilIdle()
        val dialog = vm.uiState.value.dialog
        assertTrue(dialog is DomainDialog.Edit)
        assertEquals("编程", (dialog as DomainDialog.Edit).domain.name)
    }

    @Test
    fun `hasDuplicate detects same name across visible and hidden`() = runTest(testDispatcher) {
        val dao = FakeDomainDao()
        seed(dao, listOf("编程" to false, "拳击" to true))
        val store = FakeSelectedDomainStore()

        val vm = newVm(dao, store)
        advanceUntilIdle()
        assertTrue(vm.hasDuplicate("编程"))
        assertTrue(vm.hasDuplicate("拳击")) // 隐藏的也算
        assertFalse(vm.hasDuplicate("编程", excludeId = 1L)) // 排除自身
        assertFalse(vm.hasDuplicate("不存在的领域"))
    }

    @Test
    fun `unhide restores domain to visible without auto-selecting`() = runTest(testDispatcher) {
        val dao = FakeDomainDao()
        seed(dao, listOf("编程" to false, "拳击" to true))
        val store = FakeSelectedDomainStore(initial = 1L) // 选中"编程"

        val vm = newVm(dao, store)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.domains.size)
        assertEquals(1, vm.uiState.value.hiddenDomains.size)

        vm.unhide(2L) // 恢复"拳击"
        advanceUntilIdle()
        val after = vm.uiState.value
        assertEquals(2, after.domains.size) // 重新可见
        assertEquals(0, after.hiddenDomains.size)
        assertEquals(1L, after.selectedId) // 选中不变(不自动选中恢复项)
    }
}

/** 内存假 Store:MutableStateFlow 持有当前 id,无 IO,时序受测试 dispatcher 控制。 */
private class FakeSelectedDomainStore(initial: Long? = null) : SelectedDomainStore {
    private val _state = MutableStateFlow(initial)
    val current: Long? get() = _state.value
    override val flow: Flow<Long?> = _state.asStateFlow()
    override suspend fun set(id: Long?) { _state.value = id }
}

/**
 * 内存假 Dao:绕过 Room,直接用 MutableStateFlow 持有领域列表。
 * 维护自增 id;hidden 控制可见/隐藏;observe* 返回过滤后的快照 Flow。
 */
private class FakeDomainDao : DomainDao {
    private val all = MutableStateFlow<List<Domain>>(emptyList())
    private var counter = 0L

    fun nextId(): Long { counter += 1; return counter }

    fun upsert(domain: Domain) {
        all.update { list ->
            val idx = list.indexOfFirst { it.id == domain.id }
            if (idx >= 0) list.toMutableList().apply { set(idx, domain) }
            else list + domain
        }
    }

    // 导入 feature 扩展,Fake 不涉及,空实现。
    override suspend fun insertAll(domains: List<Domain>) {}

    override suspend fun deleteAll() {}

    override suspend fun countAll(): Int = 0

    override suspend fun insert(domain: Domain): Long {
        // Repository.create 传进来的 Domain id=0(自增占位),分配真实 id。
        val id = if (domain.id == 0L) nextId() else domain.id
        upsert(domain.copy(id = id))
        return id
    }

    override suspend fun update(domain: Domain) = upsert(domain)

    override fun observeAll(): Flow<List<Domain>> =
        all.map { list -> list.sortedBy { it.createdAt } }

    override fun observeVisible(): Flow<List<Domain>> =
        all.map { list -> list.filter { !it.hidden }.sortedBy { it.createdAt } }

    override suspend fun getById(id: Long): Domain? = all.value.firstOrNull { it.id == id }

    override fun observeById(id: Long): Flow<Domain?> = all.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun setHidden(id: Long, hidden: Boolean) {
        all.update { list -> list.map { if (it.id == id) it.copy(hidden = hidden) else it } }
    }
}
