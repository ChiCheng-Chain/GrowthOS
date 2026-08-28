package com.growthos.app.ui.settings

import com.growthos.app.data.export.DataExporter
import com.growthos.app.data.export.DataImporter
import com.growthos.app.data.export.ImportCounts
import com.growthos.app.data.export.ImportException
import com.growthos.app.data.export.ImportPreview
import com.growthos.app.data.export.TableCounts
import com.growthos.app.data.local.ThemeStore
import com.growthos.app.ui.theme.GrowthThemePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * [SettingsViewModel] 状态机单测(阶段 7 / 设计 §测试;导入 feature 2026-08-27 扩展)。
 *
 * 套路同 [com.growthos.app.ui.record.SampleViewModelTest]:StandardTestDispatcher 控时序,
 * fake DataExporter/DataImporter 双桩注入(不引 Room)。验证 export/import 状态流转、
 * 防重复与双向互斥(设计 D5/D6)。
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

    /** 可控行为的导入桩:成功返回 preview;或 parse/apply 抛指定异常。 */
    private class FakeDataImporter(
        val preview: ImportPreview = samplePreview(),
        val counts: ImportCounts = ImportCounts(sampleCounts()),
        private val throwOnParse: Throwable? = null,
        private val throwOnApply: Throwable? = null,
        private val parseDelayMs: Long = 0
    ) : DataImporter {
        var applyCalled = 0
        var parseCalled = 0

        override suspend fun parse(json: String): ImportPreview {
            parseCalled++
            if (parseDelayMs > 0) delay(parseDelayMs)
            throwOnParse?.let { throw it }
            return preview
        }

        override suspend fun apply(preview: ImportPreview): ImportCounts {
            applyCalled++
            throwOnApply?.let { throw it }
            return counts
        }
    }


    /** 主题偏好桩:MutableStateFlow 驱动,记录写入。 */
    private class FakeThemeStore(initial: GrowthThemePreset? = null) : ThemeStore {
        val state = MutableStateFlow(initial)
        override val flow: Flow<GrowthThemePreset?> = state
        override suspend fun set(preset: GrowthThemePreset) {
            state.value = preset
        }
    }

    private fun vm(
        exporter: DataExporter = FakeDataExporter(),
        importer: DataImporter = FakeDataImporter(),
        themeStore: ThemeStore = FakeThemeStore()
    ) = SettingsViewModel(exporter, importer, themeStore)

    // ---------- 导出(既有用例,Factory 适配双参数) ----------

    @Test
    fun `export emits Ready with json`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(FakeDataExporter(json = "PAYLOAD"), FakeDataImporter(), FakeThemeStore())
        assertEquals(ExportState.Idle, vm.uiState.value.exportState)

        vm.export()
        advanceUntilIdle()

        val state = vm.uiState.value.exportState
        assertTrue("应为 Ready,实际 $state", state is ExportState.Ready)
        assertEquals("PAYLOAD", (state as ExportState.Ready).json)
    }

    @Test
    fun `export failure emits Failed`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(FakeDataExporter(throwOnExport = RuntimeException("disk")), FakeDataImporter(), FakeThemeStore())

        vm.export()
        advanceUntilIdle()

        val state = vm.uiState.value.exportState
        assertTrue("应为 Failed,实际 $state", state is ExportState.Failed)
        assertTrue((state as ExportState.Failed).reason.contains("disk"))
    }

    @Test
    fun `onConsumed transitions Ready to Exporting`() = runTest(testDispatcher) {
        val vm = vm()
        vm.export()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.exportState is ExportState.Ready)

        vm.onConsumed()
        assertEquals(ExportState.Exporting, vm.uiState.value.exportState)
    }

    @Test
    fun `onWritten transitions to Success`() = runTest(testDispatcher) {
        val vm = vm()
        vm.export()
        advanceUntilIdle()
        vm.onConsumed()
        vm.onWritten()
        assertEquals(ExportState.Success, vm.uiState.value.exportState)
    }

    @Test
    fun `onFailed transitions to Failed`() = runTest(testDispatcher) {
        val vm = vm()
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
        val vm = vm()
        vm.export()
        advanceUntilIdle()
        vm.onConsumed()
        vm.onWritten()
        vm.reset()
        assertEquals(ExportState.Idle, vm.uiState.value.exportState)
    }

    @Test
    fun `export while ready is ignored`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(FakeDataExporter(json = "FIRST"), FakeDataImporter(), FakeThemeStore())
        vm.export()
        advanceUntilIdle()
        // 此时 Ready(FIRST)。再调 export 应被忽略(仍 Ready/FIRST,不重算)
        vm.export()
        advanceUntilIdle()

        val state = vm.uiState.value.exportState
        assertTrue(state is ExportState.Ready)
        assertEquals("FIRST", (state as ExportState.Ready).json)
    }

    // ---------- 导入状态机(设计 D5) ----------

    @Test
    fun `import emits Confirming with preview`() = runTest(testDispatcher) {
        val fake = FakeDataImporter()
        val vm = SettingsViewModel(FakeDataExporter(), fake, FakeThemeStore())

        vm.import("JSON")
        advanceUntilIdle()

        val state = vm.uiState.value.importState
        assertTrue("应为 Confirming,实际 $state", state is ImportState.Confirming)
        assertEquals(fake.preview, (state as ImportState.Confirming).preview)
        assertEquals(1, fake.parseCalled)
    }

    @Test
    fun `import parse failure emits Failed with reason`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(
            FakeDataExporter(),
            FakeDataImporter(throwOnParse = ImportException("不支持的备份版本(v99)")),
            FakeThemeStore()
        )

        vm.import("JSON")
        advanceUntilIdle()

        val state = vm.uiState.value.importState
        assertTrue("应为 Failed,实际 $state", state is ImportState.Failed)
        assertEquals("不支持的备份版本(v99)", (state as ImportState.Failed).reason)
    }

    @Test
    fun `import unexpected exception emits Failed with prefix`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(
            FakeDataExporter(),
            FakeDataImporter(throwOnParse = RuntimeException("boom")),
            FakeThemeStore()
        )

        vm.import("JSON")
        advanceUntilIdle()

        val state = vm.uiState.value.importState
        assertTrue(state is ImportState.Failed)
        assertTrue((state as ImportState.Failed).reason.contains("boom"))
    }

    @Test
    fun `onImportConfirmed transitions to Success with counts`() = runTest(testDispatcher) {
        val fake = FakeDataImporter()
        val vm = SettingsViewModel(FakeDataExporter(), fake, FakeThemeStore())

        vm.import("JSON")
        advanceUntilIdle()
        vm.onImportConfirmed()
        advanceUntilIdle()

        val state = vm.uiState.value.importState
        assertTrue("应为 Success,实际 $state", state is ImportState.Success)
        assertEquals(fake.counts, (state as ImportState.Success).counts)
        assertEquals(1, fake.applyCalled)
    }

    @Test
    fun `onImportConfirmed apply failure emits Failed`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(
            FakeDataExporter(),
            FakeDataImporter(throwOnApply = RuntimeException("sqlite")),
            FakeThemeStore()
        )

        vm.import("JSON")
        advanceUntilIdle()
        vm.onImportConfirmed()
        advanceUntilIdle()

        val state = vm.uiState.value.importState
        assertTrue(state is ImportState.Failed)
        assertTrue((state as ImportState.Failed).reason.contains("sqlite"))
    }

    @Test
    fun `onImportCancelled returns to Idle without apply`() = runTest(testDispatcher) {
        val fake = FakeDataImporter()
        val vm = SettingsViewModel(FakeDataExporter(), fake, FakeThemeStore())

        vm.import("JSON")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.importState is ImportState.Confirming)

        vm.onImportCancelled()

        assertEquals(ImportState.Idle, vm.uiState.value.importState)
        assertEquals("取消不得触发写库", 0, fake.applyCalled)
    }

    @Test
    fun `onImportConfirmed outside Confirming is ignored`() = runTest(testDispatcher) {
        val fake = FakeDataImporter()
        val vm = SettingsViewModel(FakeDataExporter(), fake, FakeThemeStore())

        vm.onImportConfirmed() // Idle 态直接确认,应无效果
        advanceUntilIdle()

        assertEquals(ImportState.Idle, vm.uiState.value.importState)
        assertEquals(0, fake.applyCalled)
    }

    @Test
    fun `import while Confirming is ignored`() = runTest(testDispatcher) {
        val fake = FakeDataImporter()
        val vm = SettingsViewModel(FakeDataExporter(), fake, FakeThemeStore())

        vm.import("JSON")
        advanceUntilIdle()
        vm.import("JSON2")
        advanceUntilIdle()

        assertEquals("防重复触发 parse", 1, fake.parseCalled)
        assertTrue(vm.uiState.value.importState is ImportState.Confirming)
    }

    @Test
    fun `reset returns import Success to Idle`() = runTest(testDispatcher) {
        val vm = vm()
        vm.import("JSON")
        advanceUntilIdle()
        vm.onImportConfirmed()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.importState is ImportState.Success)

        vm.reset()
        assertEquals(ImportState.Idle, vm.uiState.value.importState)
    }

    // ---------- 双向互斥(设计 D6 / AC-06) ----------

    @Test
    fun `import is ignored while exporting`() = runTest(testDispatcher) {
        val fakeImporter = FakeDataImporter()
        val vm = SettingsViewModel(
            FakeDataExporter(delayMs = 100),
            fakeImporter,
            FakeThemeStore()
        )
        vm.export()
        advanceUntilIdle() // Exporting 中(export 挂在 delay)

        vm.import("JSON")
        advanceUntilIdle()

        assertEquals("导出中 import 应被忽略,不触发 parse", 0, fakeImporter.parseCalled)
        assertTrue(vm.uiState.value.importState is ImportState.Idle)
    }

    @Test
    fun `import is ignored while export Ready`() = runTest(testDispatcher) {
        val fakeImporter = FakeDataImporter()
        val vm = SettingsViewModel(FakeDataExporter(), fakeImporter, FakeThemeStore())
        vm.export()
        advanceUntilIdle() // Ready
        assertTrue(vm.uiState.value.exportState is ExportState.Ready)

        vm.import("JSON")
        advanceUntilIdle()

        assertEquals("Ready 态 import 应被忽略", 0, fakeImporter.parseCalled)
    }

    @Test
    fun `export is ignored while parsing`() = runTest(testDispatcher) {
        val fakeExporter = FakeDataExporter()
        val vm = SettingsViewModel(
            fakeExporter,
            FakeDataImporter(parseDelayMs = 100),
            FakeThemeStore()
        )
        vm.import("JSON")
        advanceUntilIdle() // Parsing 中(parse 挂在 delay)

        vm.export()
        advanceUntilIdle()

        assertEquals("导入中 export 应被忽略", ExportState.Idle, vm.uiState.value.exportState)
    }

    @Test
    fun `export is ignored while Confirming`() = runTest(testDispatcher) {
        val vm = vm()
        vm.import("JSON")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.importState is ImportState.Confirming)

        vm.export()
        advanceUntilIdle()

        assertEquals("确认框弹出期间 export 应被忽略", ExportState.Idle, vm.uiState.value.exportState)
    }

    @Test
    fun `export is ignored while importing`() = runTest(testDispatcher) {
        val fakeImporter = FakeDataImporter(parseDelayMs = 0)
        val vm = SettingsViewModel(FakeDataExporter(), fakeImporter, FakeThemeStore())
        vm.import("JSON")
        advanceUntilIdle()
        vm.onImportConfirmed()
        // Importing 中(apply 无 delay,但 onImportConfirmed 同步置 Importing 后挂起)
        vm.export()
        advanceUntilIdle()

        // 互斥:importing 窗口内 export 被忽略。apply 完成后 import=Success,export 仍 Idle
        assertEquals(ExportState.Idle, vm.uiState.value.exportState)
        assertTrue(vm.uiState.value.importState is ImportState.Success)
    }

    // ---------- 提示后复位不误伤另一侧(BR-9) ----------

    @Test
    fun `export reset does not clobber import Confirming`() = runTest(testDispatcher) {
        val vm = vm()
        vm.import("JSON")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.importState is ImportState.Confirming)

        vm.onFailed("导出未完成") // 导出侧 Failed
        vm.reset() // 复位导出侧

        assertEquals(ExportState.Idle, vm.uiState.value.exportState)
        assertTrue("导入确认态不受导出复位影响", vm.uiState.value.importState is ImportState.Confirming)
    }

    // ---------- 主题(设计 D4/D5,feature 2026-08-28) ----------

    @Test
    fun `theme flow mirrors store with default for null`() = runTest(testDispatcher) {
        val store = FakeThemeStore(initial = null)
        val vm = SettingsViewModel(FakeDataExporter(), FakeDataImporter(), store)
        advanceUntilIdle()

        assertEquals("无偏好应归一为默认", GrowthThemePreset.DEFAULT, vm.theme.value)

        store.state.value = GrowthThemePreset.Blueprint
        advanceUntilIdle()
        assertEquals("store 更新应镜像到 theme", GrowthThemePreset.Blueprint, vm.theme.value)
    }

    @Test
    fun `setTheme writes to store`() = runTest(testDispatcher) {
        val store = FakeThemeStore(initial = GrowthThemePreset.Limestone)
        val vm = SettingsViewModel(FakeDataExporter(), FakeDataImporter(), store)

        vm.setTheme(GrowthThemePreset.Vermilion)
        advanceUntilIdle()

        assertEquals(GrowthThemePreset.Vermilion, store.state.value)
        assertEquals("回声驱动 UI(非乐观更新)", GrowthThemePreset.Vermilion, vm.theme.value)
    }

    @Test
    fun `reset does not clobber theme`() = runTest(testDispatcher) {
        val store = FakeThemeStore(initial = GrowthThemePreset.PineSmoke)
        val vm = SettingsViewModel(FakeDataExporter(), FakeDataImporter(), store)
        advanceUntilIdle()

        vm.export()
        advanceUntilIdle()
        vm.onConsumed()
        vm.onWritten()
        vm.reset()

        assertEquals("reset 只复位导出/导入终态,不波及主题", GrowthThemePreset.PineSmoke, vm.theme.value)
    }
}


private fun sampleCounts() = TableCounts(
    domains = 2, errorTypes = 8, samples = 34,
    trainings = 5, principles = 8, knowledges = 9
)

private fun samplePreview() = ImportPreview(
    payload = com.growthos.app.data.export.ExportPayload(
        domains = emptyList(), errorTypes = emptyList(), samples = emptyList(),
        trainings = emptyList(), principles = emptyList(), knowledges = emptyList(),
        meta = com.growthos.app.data.export.ExportMeta(version = 2, exportedAt = 1000L)
    ),
    version = 2,
    exportedAt = 1000L,
    backupCounts = sampleCounts(),
    currentCounts = sampleCounts()
)
