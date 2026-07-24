package com.growthos.app.ui.sample_list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.local.relation.ErrorTypeCount
import com.growthos.app.data.local.relation.SampleWithErrorType
import com.growthos.app.data.repository.SampleRepository
import com.growthos.app.domain.model.Attribution
import com.growthos.app.ui.domain_view.SampleFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * 样本全量列表页状态。对齐 [com.growthos.app.ui.domain_view.DomainStatsViewModel] 的筛选范式,
 * 但不封顶——领域页只预览 10 条,全量在此页展示。数据源 [SampleRepository.observeWithNames],
 * DAO 已按 recordedAt DESC 排序。筛选逻辑与 DomainStatsViewModel 同构(复制,几行不值得抽公共)。
 */
data class SampleListUiState(
    val filteredSamples: List<SampleWithErrorType> = emptyList(),
    val filter: SampleFilter = SampleFilter(),
    val availableErrorTypes: List<ErrorTypeCount> = emptyList()
) {
    val isEmpty: Boolean get() = filteredSamples.isEmpty()
}

class SampleListViewModel(
    private val sampleRepository: SampleRepository,
    private val domainId: Long
) : ViewModel() {

    private val filterState = MutableStateFlow(SampleFilter())

    val uiState: StateFlow<SampleListUiState> = combine(
        sampleRepository.observeWithNames(domainId),
        filterState
    ) { samples, filter ->
        SampleListUiState(
            filteredSamples = applyFilter(samples, filter),
            filter = filter,
            availableErrorTypes = aggregateErrorTypes(samples)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SampleListUiState()
    )

    fun filterByErrorType(errorTypeId: Long?) {
        filterState.value = filterState.value.copy(errorTypeId = errorTypeId)
    }

    fun filterByAttribution(attribution: Attribution?) {
        filterState.value = filterState.value.copy(attribution = attribution)
    }

    private fun applyFilter(
        samples: List<SampleWithErrorType>,
        filter: SampleFilter
    ): List<SampleWithErrorType> {
        var result = samples
        if (filter.errorTypeId != null) {
            result = result.filter { it.sample.errorTypeId == filter.errorTypeId }
        }
        if (filter.attribution != null) {
            result = result.filter { it.sample.attribution == filter.attribution }
        }
        return result
    }

    /** 从全量样本聚合错误类型计数,供筛选条显示每项数量。 */
    private fun aggregateErrorTypes(samples: List<SampleWithErrorType>): List<ErrorTypeCount> =
        samples.groupBy { it.sample.errorTypeId }
            .map { (id, list) -> ErrorTypeCount(id, list.first().errorTypeName, list.size) }
            .sortedByDescending { it.count }

    class Factory(
        private val sampleRepository: SampleRepository,
        private val domainId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SampleListViewModel(sampleRepository, domainId) as T
    }
}
