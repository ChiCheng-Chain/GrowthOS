package com.growthos.app.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.Knowledge
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.KnowledgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 知识库列表页状态。
 *
 * 全部知识来自 [KnowledgeRepository.observeAllWithDomainName](带领域名,LEFT JOIN 容错),
 * 按 createdAt 倒序。支持按领域筛选([domainFilter]):null = 全部,非 null = 单领域。
 * 可删(末端对象,直接确认);待办可切换完成状态。
 */
data class KnowledgeListUiState(
    val knowledges: List<com.growthos.app.data.local.relation.KnowledgeWithDomainName> = emptyList(),
    val domains: List<Domain> = emptyList(),
    val domainFilter: Long? = null    // null = 全部
) {
    /** 筛选后的知识列表。 */
    val filteredKnowledges: List<com.growthos.app.data.local.relation.KnowledgeWithDomainName>
        get() = if (domainFilter == null) knowledges
        else knowledges.filter { it.knowledge.domainId == domainFilter }

    val isEmpty: Boolean get() = filteredKnowledges.isEmpty()
}

class KnowledgeListViewModel(
    private val knowledgeRepository: KnowledgeRepository,
    private val domainRepository: DomainRepository
) : ViewModel() {

    private val filterState = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<KnowledgeListUiState> = combine(
        knowledgeRepository.observeAllWithDomainName(),
        domainRepository.observeVisible(),
        filterState
    ) { knowledges, domains, filter ->
        KnowledgeListUiState(knowledges, domains, filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = KnowledgeListUiState()
    )

    /** 按领域筛选(null = 全部)。 */
    fun filterByDomain(domainId: Long?) {
        filterState.value = domainId
    }

    /** 删除知识。列表 Flow 自动刷新。 */
    fun delete(knowledge: Knowledge) = viewModelScope.launch {
        knowledgeRepository.delete(knowledge)
    }

    /** 切换待办完成状态。 */
    fun toggleDone(knowledge: Knowledge) = viewModelScope.launch {
        knowledgeRepository.setDone(knowledge.id, !knowledge.done)
    }

    class Factory(
        private val knowledgeRepository: KnowledgeRepository,
        private val domainRepository: DomainRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            KnowledgeListViewModel(knowledgeRepository, domainRepository) as T
    }
}
