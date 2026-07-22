package com.growthos.app.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.local.relation.TrainingWithNames
import com.growthos.app.data.repository.TrainingRepository
import com.growthos.app.domain.model.TrainingStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 训练项列表页状态(二期阶段 5 / 设计 §状态设计)。
 *
 * 全部训练项(进行中 / 已完成 / 已放弃)来自 [TrainingRepository.observeAllWithNames],
 * DAO 已按状态+时间排序。点训练项进效果页;进行中的可结束(对话框选完成/放弃,D3)。
 */
data class TrainingListUiState(
    val trainings: List<TrainingWithNames> = emptyList()
) {
    val isEmpty: Boolean get() = trainings.isEmpty()
}

class TrainingListViewModel(
    private val trainingRepository: TrainingRepository
) : ViewModel() {

    val uiState: StateFlow<TrainingListUiState> = trainingRepository.observeAllWithNames()
        .map { TrainingListUiState(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = TrainingListUiState()
        )

    /** 结束训练项(D3):置状态 + 记 endedAt。列表 Flow 自动刷新。 */
    fun finishTraining(id: Long, status: TrainingStatus) = viewModelScope.launch {
        trainingRepository.finish(id, status)
    }

    class Factory(
        private val trainingRepository: TrainingRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TrainingListViewModel(trainingRepository) as T
    }
}
