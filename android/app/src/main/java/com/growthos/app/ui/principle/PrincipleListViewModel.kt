package com.growthos.app.ui.principle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.Principle
import com.growthos.app.data.local.relation.PrincipleWithNames
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.PrincipleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 原则列表页状态(二期阶段 6 / 设计 §状态设计)。
 *
 * 全部原则来自 [PrincipleRepository.observeAllWithNames](带领域名+错误类型名,LEFT JOIN 容错),
 * 按 createdAt 倒序。点原则进编辑页;可删(D5 确认)。
 *
 * 领域筛选(feature 2026-08-27 BR-3):UI 内存过滤,null=全部;
 * 领域选项来自 observeVisible(不含已隐藏)。
 */
data class PrincipleListUiState(
    val principles: List<PrincipleWithNames> = emptyList(),
    val availableDomains: List<Domain> = emptyList(),
    val domainFilter: Long? = null
) {
    val filteredPrinciples: List<PrincipleWithNames>
        get() = if (domainFilter == null) principles
        else principles.filter { it.principle.domainId == domainFilter }
    val isEmpty: Boolean get() = filteredPrinciples.isEmpty()
}

class PrincipleListViewModel(
    private val principleRepository: PrincipleRepository,
    domainRepository: DomainRepository
) : ViewModel() {

    private val filterState = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<PrincipleListUiState> = combine(
        principleRepository.observeAllWithNames(),
        domainRepository.observeVisible(),
        filterState
    ) { principles, domains, filter ->
        PrincipleListUiState(
            principles = principles,
            availableDomains = domains,
            domainFilter = filter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PrincipleListUiState()
    )

    /** 领域筛选(null=全部)。 */
    fun filterByDomain(domainId: Long?) {
        filterState.value = domainId
    }

    /** 删除原则(R-014)。列表 Flow 自动刷新。 */
    fun delete(principle: Principle) = viewModelScope.launch {
        principleRepository.delete(principle)
    }

    class Factory(
        private val principleRepository: PrincipleRepository,
        private val domainRepository: DomainRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PrincipleListViewModel(principleRepository, domainRepository) as T
    }
}
