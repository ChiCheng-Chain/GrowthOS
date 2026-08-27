package com.growthos.app.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.relation.TrainingWithNames
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.TrainingRepository
import com.growthos.app.domain.model.TrainingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 训练项列表页状态(二期阶段 5 / 设计 §状态设计)。
 *
 * 全部训练项(进行中 / 已完成 / 已放弃)来自 [TrainingRepository.observeAllWithNames],
 * DAO 已按状态+时间排序。点训练项进效果页;进行中的可结束(对话框选完成/放弃,D3)。
 *
 * 领域筛选(feature 2026-08-27 BR-3):UI 内存过滤,null=全部;
 * 领域选项来自 observeVisible(不含已隐藏)。
 */
data class TrainingListUiState(
    val trainings: List<TrainingWithNames> = emptyList(),
    val availableDomains: List<Domain> = emptyList(),
    val domainFilter: Long? = null
) {
    val filteredTrainings: List<TrainingWithNames>
        get() = if (domainFilter == null) trainings
        else trainings.filter { it.training.domainId == domainFilter }
    val isEmpty: Boolean get() = filteredTrainings.isEmpty()
}

class TrainingListViewModel(
    private val trainingRepository: TrainingRepository,
    domainRepository: DomainRepository
) : ViewModel() {

    private val filterState = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<TrainingListUiState> = combine(
        trainingRepository.observeAllWithNames(),
        domainRepository.observeVisible(),
        filterState
    ) { trainings, domains, filter ->
        TrainingListUiState(
            trainings = trainings,
            availableDomains = domains,
            domainFilter = filter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TrainingListUiState()
    )

    /** 领域筛选(null=全部)。 */
    fun filterByDomain(domainId: Long?) {
        filterState.value = domainId
    }

    /** 结束训练项(D3):置状态 + 记 endedAt。列表 Flow 自动刷新。 */
    fun finishTraining(id: Long, status: TrainingStatus) = viewModelScope.launch {
        trainingRepository.finish(id, status)
    }

    /** 物理删除已结束训练项(CRUD 补全)。列表 Flow 自动刷新。 */
    fun deleteTraining(id: Long) = viewModelScope.launch {
        trainingRepository.deleteById(id)
    }

    class Factory(
        private val trainingRepository: TrainingRepository,
        private val domainRepository: DomainRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TrainingListViewModel(trainingRepository, domainRepository) as T
    }
}
