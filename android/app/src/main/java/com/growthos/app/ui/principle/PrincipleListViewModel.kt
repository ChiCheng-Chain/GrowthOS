package com.growthos.app.ui.principle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.local.entity.Principle
import com.growthos.app.data.local.relation.PrincipleWithNames
import com.growthos.app.data.repository.PrincipleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 原则列表页状态(二期阶段 6 / 设计 §状态设计)。
 *
 * 全部原则来自 [PrincipleRepository.observeAllWithNames](带领域名+错误类型名,LEFT JOIN 容错),
 * 按 createdAt 倒序。点原则进编辑页;可删(D5 确认)。
 */
data class PrincipleListUiState(
    val principles: List<PrincipleWithNames> = emptyList()
) {
    val isEmpty: Boolean get() = principles.isEmpty()
}

class PrincipleListViewModel(
    private val principleRepository: PrincipleRepository
) : ViewModel() {

    val uiState: StateFlow<PrincipleListUiState> = principleRepository.observeAllWithNames()
        .map { PrincipleListUiState(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PrincipleListUiState()
        )

    /** 删除原则(R-014)。列表 Flow 自动刷新。 */
    fun delete(principle: Principle) = viewModelScope.launch {
        principleRepository.delete(principle)
    }

    class Factory(
        private val principleRepository: PrincipleRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PrincipleListViewModel(principleRepository) as T
    }
}
