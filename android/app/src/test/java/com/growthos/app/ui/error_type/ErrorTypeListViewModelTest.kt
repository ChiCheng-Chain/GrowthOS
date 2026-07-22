package com.growthos.app.ui.error_type

import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.data.repository.ErrorTypeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
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
 * [ErrorTypeListViewModel] 单测(CRUD 补全)。
 *
 * 套路同 [com.growthos.app.ui.record.SampleViewModelTest]:内存假 Repository 喂 ViewModel,
 * StandardTestDispatcher 控时序,不引 Room 异步 Flow(避 Room IO 与 testDispatcher 不同步)。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ErrorTypeListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newVm(repo: FakeErrorTypeRepository): ErrorTypeListViewModel =
        ErrorTypeListViewModel(repo)

    @Test
    fun `hasDuplicate detects existing name excluding self`() = runTest(testDispatcher) {
        val repo = FakeErrorTypeRepository()
        val id = repo.seed("自定义A")
        val vm = newVm(repo)
        advanceUntilIdle()

        assertTrue(vm.hasDuplicate("自定义A", excludeId = null))
        assertTrue(!vm.hasDuplicate("自定义A", excludeId = id))
        assertTrue(!vm.hasDuplicate("不存在的名字"))
    }

    @Test
    fun `create adds error type via getOrCreate`() = runTest(testDispatcher) {
        val repo = FakeErrorTypeRepository()
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.create("全新类型")
        advanceUntilIdle()

        assertTrue(repo.all.value.any { it.name == "全新类型" })
    }

    @Test
    fun `requestDeleteErrorType emits Blocked when referenced`() = runTest(testDispatcher) {
        val repo = FakeErrorTypeRepository()
        val id = repo.seed("被引用项")
        repo.setSampleRefCount(id, 1) // 1 条样本引用
        val vm = newVm(repo)
        advanceUntilIdle()

        val collected = mutableListOf<ErrorTypeDeleteEvent>()
        val job = launch { vm.deleteEvents.toList(collected) }
        advanceUntilIdle()

        val et = vm.uiState.value.errorTypes.first { it.id == id }
        vm.requestDeleteErrorType(et)
        advanceUntilIdle()
        job.cancel()

        assertTrue(collected.firstOrNull() is ErrorTypeDeleteEvent.Blocked)
        assertEquals(1, (collected.first() as ErrorTypeDeleteEvent.Blocked).referenceCount)
        assertTrue(repo.all.value.any { it.id == id }) // 未删
    }

    @Test
    fun `requestDeleteErrorType emits ConfirmDelete when not referenced`() = runTest(testDispatcher) {
        val repo = FakeErrorTypeRepository()
        val id = repo.seed("无引用项")
        val vm = newVm(repo)
        advanceUntilIdle()

        val collected = mutableListOf<ErrorTypeDeleteEvent>()
        val job = launch { vm.deleteEvents.toList(collected) }
        advanceUntilIdle()

        val et = vm.uiState.value.errorTypes.first { it.id == id }
        vm.requestDeleteErrorType(et)
        advanceUntilIdle()
        job.cancel()

        assertTrue(collected.firstOrNull() is ErrorTypeDeleteEvent.ConfirmDelete)
        assertTrue(repo.all.value.any { it.id == id }) // 未真正删
    }

    @Test
    fun `confirmDeleteErrorType removes type`() = runTest(testDispatcher) {
        val repo = FakeErrorTypeRepository()
        val id = repo.seed("待删项")
        val vm = newVm(repo)
        advanceUntilIdle()

        val et = vm.uiState.value.errorTypes.first { it.id == id }
        vm.confirmDeleteErrorType(et)
        advanceUntilIdle()

        assertTrue(repo.all.value.none { it.id == id })
    }

    @Test
    fun `rename delegates to repository`() = runTest(testDispatcher) {
        val repo = FakeErrorTypeRepository()
        val id = repo.seed("原名")
        val vm = newVm(repo)
        advanceUntilIdle()

        vm.rename(id, "改名后")
        advanceUntilIdle()

        assertEquals("改名后", repo.all.value.first { it.id == id }.name)
    }
}

/**
 * 内存假 ErrorTypeRepository:实现 [ErrorTypeRepository] 契约,
 * MutableStateFlow 驱动 observeAll(同步发射,与 testDispatcher 同步)。
 */
private class FakeErrorTypeRepository : ErrorTypeRepository {
    val all = MutableStateFlow<List<ErrorType>>(emptyList())
    private var counter = 0L
    private val sampleRefCount = mutableMapOf<Long, Int>()

    fun seed(name: String): Long {
        counter += 1
        val id = counter
        all.update { it + ErrorType(id = id, name = name, createdAt = id) }
        return id
    }

    fun setSampleRefCount(id: Long, count: Int) { sampleRefCount[id] = count }

    override fun observeAll(): Flow<List<ErrorType>> = all.asStateFlow()

    override suspend fun getById(id: Long): ErrorType? = all.value.firstOrNull { it.id == id }

    override suspend fun getOrCreate(name: String): Long {
        all.value.firstOrNull { it.name == name }?.let { return it.id }
        counter += 1
        val id = counter
        all.update { it + ErrorType(id = id, name = name, createdAt = id) }
        return id
    }

    override suspend fun rename(id: Long, name: String) {
        val target = all.value.firstOrNull { it.id == id } ?: return
        val trimmed = name.trim()
        if (target.name == trimmed) return
        all.update { list -> list.map { if (it.id == id) it.copy(name = trimmed) else it } }
    }

    override suspend fun referenceCount(id: Long): Int = sampleRefCount[id] ?: 0

    override suspend fun delete(id: Long) {
        all.update { list -> list.filterNot { it.id == id } }
    }
}
