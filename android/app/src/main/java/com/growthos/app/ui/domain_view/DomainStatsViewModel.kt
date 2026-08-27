package com.growthos.app.ui.domain_view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.local.SelectedDomainStore
import com.growthos.app.data.local.entity.Knowledge
import com.growthos.app.data.local.entity.Principle
import com.growthos.app.data.local.relation.ErrorTypeCount
import com.growthos.app.data.local.relation.SampleWithErrorType
import com.growthos.app.data.local.relation.TrainingWithTypeName
import com.growthos.app.data.repository.KnowledgeRepository
import com.growthos.app.data.repository.PrincipleRepository
import com.growthos.app.data.repository.SampleRepository
import com.growthos.app.data.repository.TrainingRepository
import com.growthos.app.domain.model.Attribution
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * 领域页统计 + 筛选状态(二期阶段 3 / 设计 §状态设计)。
 *
 * 与 [com.growthos.app.ui.domain.DomainViewModel](阶段 1,管 chips / 选中 / 隐藏)并列,
 * 各管一块(设计 D1)。两者都注入 [SelectedDomainStore] 同源读 selectedDomainId。
 *
 * 四区块随 selectedDomainId 联动:`flatMapLatest { id -> combine(各数据流) }`,
 * id 变则切到新领域的流。F5 筛选用 UI 层过滤 baseSamples(设计 D2),filter 变不重查数据。
 */
data class DomainStatsUiState(
    val recentSamples: List<SampleWithErrorType> = emptyList(),
    val errorDistribution: List<ErrorTypeCount> = emptyList(),
    val inProgressTrainings: List<TrainingWithTypeName> = emptyList(),
    val recentPrinciples: List<Principle> = emptyList(),
    val recentKnowledges: List<Knowledge> = emptyList(),
    val filteredSamples: List<SampleWithErrorType> = emptyList(),
    val filter: SampleFilter = SampleFilter(),
    val hasDomain: Boolean = false,
    /** 「查看全部」计数:该领域全部训练(含已结束)/原则/知识总条数(BR-1)。 */
    val trainingTotal: Int = 0,
    val principleTotal: Int = 0,
    val knowledgeTotal: Int = 0
) {
    val availableErrorTypes: List<ErrorTypeCount> get() = errorDistribution
}

data class SampleFilter(
    val errorTypeId: Long? = null,
    val attribution: Attribution? = null
)

/** 五区块原始数据 + 全量带名样本(供筛选)+ 「查看全部」计数,内部聚合用。 */
private data class DomainData(
    val recentSamples: List<SampleWithErrorType>,
    val errorDistribution: List<ErrorTypeCount>,
    val inProgressTrainings: List<TrainingWithTypeName>,
    val recentPrinciples: List<Principle>,
    val recentKnowledges: List<Knowledge>,
    val baseSamples: List<SampleWithErrorType>,
    val hasDomain: Boolean,
    val trainingTotal: Int = 0,
    val principleTotal: Int = 0,
    val knowledgeTotal: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class DomainStatsViewModel(
    private val sampleRepository: SampleRepository,
    private val trainingRepository: TrainingRepository,
    private val principleRepository: PrincipleRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val selectedStore: SelectedDomainStore
) : ViewModel() {

    private val filterState = MutableStateFlow(SampleFilter())

    // 数据流:随 selectedDomainId 切换(flatMapLatest)。
    // principles + knowledges 先 combine 成 Pair,再并入主 combine(避免 6 路超 combine 5 参上限)。
    private val dataFlow = selectedStore.flow.flatMapLatest { domainId ->
        // principles + knowledges 先 combine 成 Pair,再并入主 combine(避免超 combine 5 参上限)
        if (domainId == null) {
            val principlesAndKnowledges = combine(
                kotlinx.coroutines.flow.flowOf(emptyList<Principle>()),
                kotlinx.coroutines.flow.flowOf(emptyList<Knowledge>())
            ) { p, k -> p to k }

            combine(
                kotlinx.coroutines.flow.flowOf(emptyList<SampleWithErrorType>()),
                kotlinx.coroutines.flow.flowOf(emptyList<ErrorTypeCount>()),
                kotlinx.coroutines.flow.flowOf(emptyList<TrainingWithTypeName>()),
                principlesAndKnowledges,
                kotlinx.coroutines.flow.flowOf(emptyList<SampleWithErrorType>())
            ) { recent, dist, trainings, pk, base ->
                val (principles, knowledges) = pk
                DomainData(
                    recent, dist, trainings, principles.take(PRINCIPLE_LIMIT), knowledges.take(KNOWLEDGE_LIMIT),
                    base, hasDomain = false, trainingTotal = 0, principleTotal = 0, knowledgeTotal = 0
                )
            }
        } else {
            val principlesAndKnowledges = combine(
                principleRepository.observeByDomain(domainId),
                knowledgeRepository.observeByDomain(domainId)
            ) { p, k -> p to k }
            // 进行中训练(预览)+ 全部训练(计数,R-1 口径:含已结束)两流并行
            val trainingsAndTotal = combine(
                trainingRepository.observeInProgressByDomainWithTypeName(domainId),
                trainingRepository.observeByDomain(domainId)
            ) { inProgress, all -> inProgress to all.size }

            combine(
                sampleRepository.observeRecentByDomain(domainId, RECENT_LIMIT),
                sampleRepository.observeTopErrorTypes(domainId, 0L, Long.MAX_VALUE, DISTRIBUTION_LIMIT),
                trainingsAndTotal,
                principlesAndKnowledges,
                sampleRepository.observeWithNames(domainId)
            ) { recent, dist, tt, pk, base ->
                val (principles, knowledges) = pk
                val (inProgress, trainingCount) = tt
                DomainData(
                    recent, dist, inProgress, principles.take(PRINCIPLE_LIMIT), knowledges.take(KNOWLEDGE_LIMIT),
                    base, hasDomain = true,
                    trainingTotal = trainingCount, principleTotal = principles.size, knowledgeTotal = knowledges.size
                )
            }
        }
    }

    val uiState: StateFlow<DomainStatsUiState> = combine(dataFlow, filterState) { data, filter ->
        DomainStatsUiState(
            recentSamples = data.recentSamples,
            errorDistribution = data.errorDistribution,
            inProgressTrainings = data.inProgressTrainings,
            recentPrinciples = data.recentPrinciples,
            recentKnowledges = data.recentKnowledges,
            filteredSamples = if (data.hasDomain) applyFilter(data.baseSamples, filter) else emptyList(),
            filter = filter,
            hasDomain = data.hasDomain,
            trainingTotal = data.trainingTotal,
            principleTotal = data.principleTotal,
            knowledgeTotal = data.knowledgeTotal
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DomainStatsUiState()
    )

    /** F5 筛选:设错误类型(null 清除)。 */
    fun filterByErrorType(errorTypeId: Long?) {
        filterState.value = filterState.value.copy(errorTypeId = errorTypeId)
    }

    /** F5 筛选:设归因(null 清除)。 */
    fun filterByAttribution(attribution: Attribution?) {
        filterState.value = filterState.value.copy(attribution = attribution)
    }

    /** 清所有筛选。 */
    fun clearFilter() {
        filterState.value = SampleFilter()
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

    class Factory(
        private val sampleRepository: SampleRepository,
        private val trainingRepository: TrainingRepository,
        private val principleRepository: PrincipleRepository,
        private val knowledgeRepository: KnowledgeRepository,
        private val store: SelectedDomainStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DomainStatsViewModel(sampleRepository, trainingRepository, principleRepository, knowledgeRepository, store) as T
    }

    private companion object {
        const val RECENT_LIMIT = 5
        const val DISTRIBUTION_LIMIT = 8
        const val PRINCIPLE_LIMIT = 3
        const val KNOWLEDGE_LIMIT = 3
    }
}
