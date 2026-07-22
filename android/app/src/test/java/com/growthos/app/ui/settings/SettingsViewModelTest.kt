package com.growthos.app.ui.settings

import com.growthos.app.data.export.DataExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * [SettingsViewModel] 状态机单测(阶段 7 / 设计 §测试)。
 *
 * 套路同 [com.growthos.app.ui.record.SampleViewModelTest]:StandardTestDispatcher 控时序,
 * fake DataExporter 注入(不引 Room)。验证 export → Ready、onWritten/onFailed 状态流转、防重复。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeDataExporter(
        private val json: String = "{\"meta\":{\"version\":1}}",
        private val delayMs: Long = 0,
        private val throwOnExport: Throwable? = null
    ) : DataExporter {
        override suspend fun export(): String {
            if (delayMs > 0) delay(delayMs)
            throwOnExport?.let { throw it }
            return json
        }
    }

    @Test
    fun `export emits Ready with json`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(FakeDataExporter(json = "PAYLOAD"))
        assertEquals(ExportState.Idle, vm.uiState.value.exportState)

        vm.export()
        advanceUntilIdle()

        val state = vm.uiState.value.exportState
        assertTrue("应为 Ready,实际 $state", state is ExportState.Ready)
        assertEquals("PAYLOAD", (state as ExportState.Ready).json)
    }

    @Test
    fun `export failure emits Failed`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(FakeDataExporter(throwOnExport = RuntimeException("disk")))

        vm.export()
        advanceUntilIdle()

        val state = vm.uiState.value.exportState
        assertTrue("应为 Failed,实际 $state", state is ExportState.Failed)
        assertTrue((state as ExportState.Failed).reason.contains("disk"))
    }

    @Test
    fun `onConsumed transitions Ready to Exporting`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(FakeDataExporter())
        vm.export()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.exportState is ExportState.Ready)

        vm.onConsumed()
        assertEquals(ExportState.Exporting, vm.uiState.value.exportState)
    }

    @Test
    fun `onWritten transitions to Success`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(FakeDataExporter())
        vm.export()
        advanceUntilIdle()
        vm.onConsumed()
        vm.onWritten()
        assertEquals(ExportState.Success, vm.uiState.value.exportState)
    }

    @Test
    fun `onFailed transitions to Failed`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(FakeDataExporter())
        vm.export()
        advanceUntilIdle()
        vm.onConsumed()
        vm.onFailed("取消")
        val state = vm.uiState.value.exportState
        assertTrue(state is ExportState.Failed)
        assertEquals("取消", (state as ExportState.Failed).reason)
    }

    @Test
    fun `reset returns to Idle`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(FakeDataExporter())
        vm.export()
        advanceUntilIdle()
        vm.onConsumed()
        vm.onWritten()
        vm.reset()
        assertEquals(ExportState.Idle, vm.uiState.value.exportState)
    }

    @Test
    fun `export while ready is ignored`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(FakeDataExporter(json = "FIRST"))
        vm.export()
        advanceUntilIdle()
        // 此时 Ready(FIRST)。再调 export 应被忽略(仍 Ready/FIRST,不重算)
        vm.export()
        advanceUntilIdle()

        val state = vm.uiState.value.exportState
        assertTrue(state is ExportState.Ready)
        assertEquals("FIRST", (state as ExportState.Ready).json)
    }
}
