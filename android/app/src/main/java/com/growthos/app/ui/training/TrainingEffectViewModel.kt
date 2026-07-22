package com.growthos.app.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.local.entity.Training
import com.growthos.app.data.local.relation.SampleWithErrorType
import com.growthos.app.data.local.relation.TrainingEffectStats
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.ErrorTypeRepository
import com.growthos.app.data.repository.SampleRepository
import com.growthos.app.data.repository.TrainingRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 训练效果页状态(二期阶段 5 / 设计 §状态设计)。
 *
 * training 一次性加载(getById),拿到 errorTypeId + startedAt 后:
 * stats 一次性(effectStats,D2 维持 suspend)+ afterSamples Flow(observeSamplesAfter),
 * 三者合成 UiState。training 为 null 时显示加载/错误态。
 */
data class TrainingEffectUiState(
    val training: Training? = null,
    val errorTypeName: String? = null,
    val domainName: String? = null,
    val stats: TrainingEffectStats? = null,
    val afterSamples: List<SampleWithErrorType> = emptyList(),
    val loaded: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingEffectViewModel(
    private val trainingRepository: TrainingRepository,
    private val sampleRepository: SampleRepository,
    private val errorTypeRepository: ErrorTypeRepository,
    private val domainRepository: DomainRepository,
    private val trainingId: Long
) : ViewModel() {

    // training 一次性加载;为 null 表示加载中或不存在。
    private val trainingState = MutableStateFlow<Training?>(null)

    init {
        viewModelScope.launch {
            trainingState.value = trainingRepository.getById(trainingId)
        }
    }

    val uiState: StateFlow<TrainingEffectUiState> = trainingState.flatMapLatest { training ->
        if (training == null) {
            flowOf(TrainingEffectUiState(loaded = false))
        } else {
            // stats 一次性(包成 Flow),afterSamples Flow;errorType/domain 名一次性查。
            val statsFlow = flow { emit(trainingRepository.effectStats(training.errorTypeId, training.startedAt)) }
            val afterFlow = sampleRepository.observeSamplesAfter(training.errorTypeId, training.startedAt)
            val nameFlow = flow {
                val et = errorTypeRepository.getById(training.errorTypeId)
                val d = domainRepository.getById(training.domainId)
                emit((et?.name ?: "未知错误") to (d?.name ?: "未知领域"))
            }
            combine(statsFlow, afterFlow, nameFlow) { stats, after, (etName, dName) ->
                TrainingEffectUiState(
                    training = training,
                    errorTypeName = etName,
                    domainName = dName,
                    stats = stats,
                    afterSamples = after,
                    loaded = true
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TrainingEffectUiState()
    )

    class Factory(
        private val trainingRepository: TrainingRepository,
        private val sampleRepository: SampleRepository,
        private val errorTypeRepository: ErrorTypeRepository,
        private val domainRepository: DomainRepository,
        private val trainingId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TrainingEffectViewModel(
                trainingRepository, sampleRepository, errorTypeRepository, domainRepository, trainingId
            ) as T
    }
}
