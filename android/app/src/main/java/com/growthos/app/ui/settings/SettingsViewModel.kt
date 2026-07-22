package com.growthos.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.export.DataExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class SettingsUiState(
    val exportState: ExportState = ExportState.Idle
)

/**
 * 设置页 ViewModel(阶段 7 / R-013)。
 *
 * export() → DataExporter 拉 JSON → emit Ready(json)。
 * Screen 检测 Ready 启动 SAF launcher,拿到 Uri 写入后回调:
 * - [onWritten]:写入成功 → Success。
 * - [onFailed]:取消 / 异常 → Failed。
 * - [onConsumed]:Screen 取走 Ready 的 json(转 Exporting,避免重复触发 launcher)。
 *
 * 用 StateFlow(非 SharedFlow):Screen 要据状态决定是否启动 launcher,
 * 且需在配置变更后恢复「等待写入」态。
 */
class SettingsViewModel(
    private val dataExporter: DataExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** 触发导出:拉全量 → 组装 JSON → emit Ready 等 Screen 写入。 */
    fun export() {
        if (_uiState.value.exportState is ExportState.Exporting ||
            _uiState.value.exportState is ExportState.Ready
        ) return // 防重复点击
        _uiState.value = SettingsUiState(ExportState.Exporting)
        viewModelScope.launch {
            try {
                val json = dataExporter.export()
                _uiState.value = SettingsUiState(ExportState.Ready(json))
            } catch (t: Throwable) {
                _uiState.value = SettingsUiState(ExportState.Failed(t.message ?: "导出失败"))
            }
        }
    }

    /** Screen 取走 Ready 的 json 后调用,转 Exporting 等待写入结果。 */
    fun onConsumed() {
        val cur = _uiState.value.exportState
        if (cur is ExportState.Ready) {
            _uiState.value = SettingsUiState(ExportState.Exporting)
        }
    }

    /** Screen 写入 Uri 成功。 */
    fun onWritten() {
        _uiState.value = SettingsUiState(ExportState.Success)
    }

    /** Screen 取消 launcher 或写入异常。 */
    fun onFailed(reason: String = "导出未完成") {
        _uiState.value = SettingsUiState(ExportState.Failed(reason))
    }

    /** 提示展示后复位到 Idle(避免旋转后重复弹提示)。 */
    fun reset() {
        _uiState.value = SettingsUiState(ExportState.Idle)
    }

    class Factory(
        private val dataExporter: DataExporter
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(dataExporter) as T
    }
}
