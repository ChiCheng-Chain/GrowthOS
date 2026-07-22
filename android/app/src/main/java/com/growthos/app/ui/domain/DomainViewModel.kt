package com.growthos.app.ui.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.local.SelectedDomainStore
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.repository.DomainRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 领域页状态(二期阶段 1 / 设计文档 §状态设计)。
 *
 * - [domains] / [hiddenDomains]:来自 repository 的可见 / 已隐藏列表。
 * - [selectedId]:有效选中(回退后)。持久值不在可见列表时回退到第一个可见;
 *   visible 空则 null。**回退不写回持久值**,避免"读→回退→写→再读"的副作用循环。
 * - [dialog]:对话框开关 + 携带编辑目标。由 [dialogState] 独立驱动,合进 combine 推送。
 */
data class DomainUiState(
    val domains: List<Domain> = emptyList(),
    val hiddenDomains: List<Domain> = emptyList(),
    val selectedId: Long? = null,
    val dialog: DomainDialog? = null
) {
    val selectedDomain: Domain? get() = domains.firstOrNull { it.id == selectedId }
    val isEmpty: Boolean get() = domains.isEmpty()
}

sealed interface DomainDialog {
    data object Create : DomainDialog
    data class Edit(val domain: Domain) : DomainDialog
}

class DomainViewModel(
    private val repository: DomainRepository,
    private val selectedStore: SelectedDomainStore
) : ViewModel() {

    private val dialogState = MutableStateFlow<DomainDialog?>(null)

    val uiState: StateFlow<DomainUiState> = combine(
        repository.observeVisible(),
        repository.observeAll(),
        selectedStore.flow,
        dialogState
    ) { visible, all, persistentId, dialog ->
        val hidden = all.filter { it.hidden }
        // 回退:持久值若不在可见列表,回退到第一个可见;不写回持久值。
        val effective = persistentId?.takeIf { id -> visible.any { it.id == id } }
            ?: visible.firstOrNull()?.id
        DomainUiState(
            domains = visible,
            hiddenDomains = hidden,
            selectedId = effective,
            dialog = dialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DomainUiState()
    )

    /** 是否存在同名领域(用于重名软提示,D2)。excludeId 用于编辑时排除自身。 */
    fun hasDuplicate(name: String, excludeId: Long? = null): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        return uiState.value.domains.any { it.id != excludeId && it.name == trimmed } ||
            uiState.value.hiddenDomains.any { it.id != excludeId && it.name == trimmed }
    }

    fun select(id: Long) = viewModelScope.launch {
        selectedStore.set(id)
    }

    fun openCreate() {
        dialogState.value = DomainDialog.Create
    }

    fun openEdit(domain: Domain) {
        dialogState.value = DomainDialog.Edit(domain)
    }

    fun dismissDialog() {
        dialogState.value = null
    }

    fun create(name: String) = viewModelScope.launch {
        val id = repository.create(name.trim())
        selectedStore.set(id) // 新建后自动选中(F2)
        dialogState.value = null
    }

    fun rename(id: Long, name: String) = viewModelScope.launch {
        repository.rename(id, name.trim())
        dialogState.value = null
    }

    fun setHidden(id: Long, hidden: Boolean) = viewModelScope.launch {
        repository.setHidden(id, hidden)
        // 编辑对话框里的隐藏开关若隐藏当前编辑领域,关闭对话框让 UI 反映隐藏后状态。
        val editing = (dialogState.value as? DomainDialog.Edit)?.domain
        if (hidden && editing?.id == id) dialogState.value = null
    }

    /** 恢复已隐藏领域(不自动选中,对齐 F4)。 */
    fun unhide(id: Long) = setHidden(id, hidden = false)

    class Factory(
        private val repository: DomainRepository,
        private val store: SelectedDomainStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DomainViewModel(repository, store) as T
    }
}
