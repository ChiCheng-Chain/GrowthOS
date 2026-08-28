package com.growthos.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.export.DataExporter
import com.growthos.app.data.export.DataImporter
import com.growthos.app.data.export.ImportCounts
import com.growthos.app.data.export.ImportException
import com.growthos.app.data.export.ImportPreview
import com.growthos.app.data.local.ThemeStore
import com.growthos.app.ui.theme.GrowthThemePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 导出状态机(阶段 7 / 设计 §状态设计)。
 *
 * - [Idle]:初始 / 已结束,无动作。
 * - [Exporting]:正在生成 JSON 或等待写入。
 * - [Ready]:JSON 已就绪,Screen 据此启动 SAF CreateDocument launcher。
 * - [Success]:已写入 Uri,提示「已导出」。
 * - [Failed]:取消 launcher / 写入异常,提示「导出未完成」。
 */
sealed interface ExportState {
    data object Idle : ExportState
    data object Exporting : ExportState
    data class Ready(val json: String) : ExportState
    data object Success : ExportState
    data class Failed(val reason: String) : ExportState
}

/**
 * 导入状态机(feature 2026-08-27 导入 JSON 备份 / 设计 D5)。
 *
 * - [Idle]:初始 / 已结束。
 * - [Parsing]:读取+解析+校验中(parse 只读不写)。
 * - [Confirming]:解析成功,preview 就绪,Screen 据此弹确认框(BR-7 双向对照)。
 * - [Importing]:确认后事务写库中。
 * - [Success]:导入完成,携带计数供「已导入:N 样本 / M 领域…」反馈(BR-6)。
 * - [Failed]:取消 / 校验失败 / 写库异常,reason 即 Snackbar 文案。
 */
sealed interface ImportState {
    data object Idle : ImportState
    data object Parsing : ImportState
    data class Confirming(val preview: ImportPreview) : ImportState
    data object Importing : ImportState
    data class Success(val counts: ImportCounts) : ImportState
    data class Failed(val reason: String) : ImportState
}

data class SettingsUiState(
    val exportState: ExportState = ExportState.Idle,
    val importState: ImportState = ImportState.Idle
)

/**
 * 设置页 ViewModel(阶段 7 / R-013;导入 feature 2026-08-27 扩展)。
 *
 * 导出:export() → DataExporter 拉 JSON → emit Ready(json)。
 * Screen 检测 Ready 启动 SAF launcher,拿到 Uri 写入后回调:
 * - [onWritten]:写入成功 → Success。
 * - [onFailed]:取消 / 异常 → Failed。
 * - [onConsumed]:Screen 取走 Ready 的 json(转 Exporting,避免重复触发 launcher)。
 *
 * 导入(两段式,对齐 parse/apply 契约):
 * - [import]:Screen 从 SAF OpenDocument 拿到文件文本后调用 → Parsing →
 *   parse 成功 emit Confirming(preview);ImportException/其他异常 → Failed(reason)。
 * - [onImportConfirmed]:确认框「替换现有数据」→ Importing →
 *   apply 成功 Success(counts) / 失败 Failed。
 * - [onImportCancelled]:确认框取消 → Idle,零副作用(BR-2)。
 *
 * 互斥(设计 D6 / BR-8):导出 Exporting/Ready 时 import() 忽略;
 * 导入 Parsing/Confirming/Importing 时 export() 忽略。纯派生,无独立标志。
 *
 * StateFlow(非 SharedFlow):Screen 据状态决定 launcher/确认框,且配置变更后可恢复。
 */
class SettingsViewModel(
    private val dataExporter: DataExporter,
    private val dataImporter: DataImporter,
    themeStore: ThemeStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /**
     * 当前主题(独立流,不并入 uiState——reset() 只复位导出/导入终态,
     * 不应波及主题选择;设计 D4)。store.flow 镜像,null 归一为默认。
     */
    val theme: StateFlow<GrowthThemePreset> = themeStore.flow
        .map { it ?: GrowthThemePreset.DEFAULT }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GrowthThemePreset.DEFAULT)

    private val themeWriter: ThemeStore = themeStore

    /** 选择主题:写 store,回声驱动 UI(不做乐观更新,设计 D5)。 */
    fun setTheme(preset: GrowthThemePreset) {
        viewModelScope.launch { themeWriter.set(preset) }
    }

    /** 触发导出:拉全量 → 组装 JSON → emit Ready 等 Screen 写入。导入进行中时忽略(互斥)。 */
    fun export() {
        val cur = _uiState.value
        if (cur.exportState is ExportState.Exporting ||
            cur.exportState is ExportState.Ready
        ) return // 防重复点击
        if (cur.importState is ImportState.Parsing ||
            cur.importState is ImportState.Confirming ||
            cur.importState is ImportState.Importing
        ) return // 互斥(BR-8)
        _uiState.value = SettingsUiState(exportState = ExportState.Exporting, importState = cur.importState)
        viewModelScope.launch {
            try {
                val json = dataExporter.export()
                _uiState.value = SettingsUiState(exportState = ExportState.Ready(json), importState = _uiState.value.importState)
            } catch (t: Throwable) {
                _uiState.value = SettingsUiState(exportState = ExportState.Failed(t.message ?: "导出失败"), importState = _uiState.value.importState)
            }
        }
    }

    /** Screen 取走 Ready 的 json 后调用,转 Exporting 等待写入结果。 */
    fun onConsumed() {
        val cur = _uiState.value
        if (cur.exportState is ExportState.Ready) {
            _uiState.value = SettingsUiState(exportState = ExportState.Exporting, importState = cur.importState)
        }
    }

    /** Screen 写入 Uri 成功。 */
    fun onWritten() {
        val cur = _uiState.value
        _uiState.value = SettingsUiState(exportState = ExportState.Success, importState = cur.importState)
    }

    /** Screen 取消 launcher 或写入异常。 */
    fun onFailed(reason: String = "导出未完成") {
        val cur = _uiState.value
        _uiState.value = SettingsUiState(exportState = ExportState.Failed(reason), importState = cur.importState)
    }

    /** 触发导入:解析+校验(只读)→ Confirming 弹确认框。导出进行中时忽略(互斥)。 */
    fun import(json: String) {
        val cur = _uiState.value
        if (cur.importState is ImportState.Parsing ||
            cur.importState is ImportState.Confirming ||
            cur.importState is ImportState.Importing
        ) return // 防重复触发
        if (cur.exportState is ExportState.Exporting ||
            cur.exportState is ExportState.Ready
        ) return // 互斥(BR-8)
        _uiState.value = SettingsUiState(exportState = cur.exportState, importState = ImportState.Parsing)
        viewModelScope.launch {
            try {
                val preview = dataImporter.parse(json)
                _uiState.value = SettingsUiState(exportState = _uiState.value.exportState, importState = ImportState.Confirming(preview))
            } catch (e: ImportException) {
                _uiState.value = SettingsUiState(exportState = _uiState.value.exportState, importState = ImportState.Failed(e.reason))
            } catch (t: Throwable) {
                _uiState.value = SettingsUiState(exportState = _uiState.value.exportState, importState = ImportState.Failed("导入失败:${t.message ?: "未知错误"}"))
            }
        }
    }

    /** 确认框「替换现有数据」:事务写库。 */
    fun onImportConfirmed() {
        val cur = _uiState.value
        val preview = (cur.importState as? ImportState.Confirming)?.preview ?: return
        _uiState.value = SettingsUiState(exportState = cur.exportState, importState = ImportState.Importing)
        viewModelScope.launch {
            try {
                val counts = dataImporter.apply(preview)
                _uiState.value = SettingsUiState(exportState = _uiState.value.exportState, importState = ImportState.Success(counts))
            } catch (t: Throwable) {
                _uiState.value = SettingsUiState(exportState = _uiState.value.exportState, importState = ImportState.Failed("导入失败:${t.message ?: "未知错误"}"))
            }
        }
    }

    /** 确认框取消:回 Idle,零副作用(数据无损)。 */
    fun onImportCancelled() {
        val cur = _uiState.value
        if (cur.importState is ImportState.Confirming) {
            _uiState.value = SettingsUiState(exportState = cur.exportState, importState = ImportState.Idle)
        }
    }

    /** 提示展示后复位到 Idle(避免旋转后重复弹提示)。导出/导入独立复位各自的态。 */
    fun reset() {
        val cur = _uiState.value
        val newExport = if (cur.exportState is ExportState.Success || cur.exportState is ExportState.Failed) ExportState.Idle else cur.exportState
        val newImport = if (cur.importState is ImportState.Success || cur.importState is ImportState.Failed) ImportState.Idle else cur.importState
        _uiState.value = SettingsUiState(exportState = newExport, importState = newImport)
    }

    class Factory(
        private val dataExporter: DataExporter,
        private val dataImporter: DataImporter,
        private val themeStore: ThemeStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(dataExporter, dataImporter, themeStore) as T
    }
}
