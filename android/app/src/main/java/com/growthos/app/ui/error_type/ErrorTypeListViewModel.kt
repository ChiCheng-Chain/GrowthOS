package com.growthos.app.ui.error_type

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.data.repository.ErrorTypeRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 错误类型管理页状态(CRUD 补全)。
 *
 * - [errorTypes]:全部错误类型(含种子),来自 ErrorTypeRepository.observeAll()。
 * - [dialog]:新建 / 改名对话框开关 + 携带编辑目标(仿 DomainViewModel)。
 *
 * 改名撞名由 Repository 封装合并逻辑(迁移引用 + 删旧 id),UI 无感。
 * 删除走引用检查(referenceCount → 拦截 / 确认),与 Sample 编辑页长按删除同范式。
 */
data class ErrorTypeListUiState(
    val errorTypes: List<ErrorType> = emptyList(),
    val dialog: ErrorTypeDialog? = null
) {
    val isEmpty: Boolean get() = errorTypes.isEmpty()
}

sealed interface ErrorTypeDialog {
    data object Create : ErrorTypeDialog
    data class Edit(val errorType: ErrorType) : ErrorTypeDialog
}

/** 删除事件(与 SampleViewModel 同结构,本页独立定义避免跨包依赖)。 */
sealed interface ErrorTypeDeleteEvent {
    data class Blocked(val referenceCount: Int) : ErrorTypeDeleteEvent
    data class ConfirmDelete(val errorType: ErrorType) : ErrorTypeDeleteEvent
    data object ErrorTypeDeleted : ErrorTypeDeleteEvent
}

class ErrorTypeListViewModel(
    private val repository: ErrorTypeRepository
) : ViewModel() {

    private val dialogState = MutableStateFlow<ErrorTypeDialog?>(null)

    private val _deleteEvents = MutableSharedFlow<ErrorTypeDeleteEvent>(extraBufferCapacity = 1)
    val deleteEvents: SharedFlow<ErrorTypeDeleteEvent> = _deleteEvents.asSharedFlow()

    val uiState: StateFlow<ErrorTypeListUiState> = combine(
        repository.observeAll(),
        dialogState
    ) { errorTypes, dialog ->
        ErrorTypeListUiState(errorTypes = errorTypes, dialog = dialog)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ErrorTypeListUiState()
    )

    /** 是否存在同名(重名软提示)。excludeId 排除编辑态自身。 */
    fun hasDuplicate(name: String, excludeId: Long? = null): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        return uiState.value.errorTypes.any { it.id != excludeId && it.name == trimmed }
    }

    fun openCreate() { dialogState.value = ErrorTypeDialog.Create }
    fun openEdit(errorType: ErrorType) { dialogState.value = ErrorTypeDialog.Edit(errorType) }
    fun dismissDialog() { dialogState.value = null }

    fun create(name: String) = viewModelScope.launch {
        repository.getOrCreate(name.trim())
        dialogState.value = null
    }

    fun rename(id: Long, name: String) = viewModelScope.launch {
        repository.rename(id, name.trim())
        dialogState.value = null
    }

    fun requestDeleteErrorType(errorType: ErrorType) = viewModelScope.launch {
        val count = repository.referenceCount(errorType.id)
        if (count > 0) {
            _deleteEvents.tryEmit(ErrorTypeDeleteEvent.Blocked(count))
        } else {
            _deleteEvents.tryEmit(ErrorTypeDeleteEvent.ConfirmDelete(errorType))
        }
    }

    fun confirmDeleteErrorType(errorType: ErrorType) = viewModelScope.launch {
        repository.delete(errorType.id)
        _deleteEvents.tryEmit(ErrorTypeDeleteEvent.ErrorTypeDeleted)
    }

    class Factory(
        private val repository: ErrorTypeRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ErrorTypeListViewModel(repository) as T
    }
}
