package com.growthos.app.ui.weekly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.relation.ControllableRatio
import com.growthos.app.data.local.relation.ErrorTypeCount
import com.growthos.app.data.local.relation.SampleWithErrorType
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.SampleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * 周复盘状态(二期阶段 4 / 设计 §状态设计)。
 *
 * 五项聚合随 [days](F6 时间范围)与 [domainFilter](F7 领域选择)联动重算:
 * `combine(days, filter) → flatMapLatest { combine(5 个查询流) }`,
 * 任一选择变化则切到新窗口/领域的流。
 *
 * 与 [com.growthos.app.ui.domain_view.DomainStatsViewModel](阶段 3)范式一致,
 * 但不复用 SelectedDomainStore —— 周复盘是独立的全局视图,默认看全部领域(domainId=0),
 * 用户在复盘页主动缩到单领域(设计 D4)。
 */
data class WeeklyUiState(
    val days: Int = 7,                                  // F6 时间范围(7/14/30)
    val domainFilter: DomainFilter = DomainFilter.All,  // F7 领域选择
    val sampleCount: Int = 0,                           // F1 本周样本数
    val topErrors: List<ErrorTypeCount> = emptyList(),  // F2 高频错误前三
    val controllableRatio: ControllableRatio? = null,   // F3 可控占比
    val highestEmotion: SampleWithErrorType? = null,    // F4 情绪强度最高
    val suggestedError: ErrorTypeCount? = null,         // F5 建议关注高频可控错误
    val availableDomains: List<Domain> = emptyList()    // F7 选项(可见领域)
)

/** F7 领域选择:全部领域(domainId=0)或单个领域。 */
sealed interface DomainFilter {
    data object All : DomainFilter
    data class Single(val domainId: Long) : DomainFilter
}

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyViewModel(
    private val sampleRepository: SampleRepository,
    private val domainRepository: DomainRepository
) : ViewModel() {

    private val daysState = MutableStateFlow(7)
    private val domainFilterState = MutableStateFlow<DomainFilter>(DomainFilter.All)

    /** 五项数据流:随 days / domainFilter 切换(flatMapLatest)。 */
    private val statsFlow = combine(daysState, domainFilterState) { days, filter ->
        days to filter
    }.flatMapLatest { (days, filter) ->
        val domainId = if (filter is DomainFilter.Single) filter.domainId else 0L
        combine(
            sampleRepository.observeCountLastNDays(days, domainId),
            sampleRepository.observeTopErrorTypesLastNDays(days, domainId, limit = 3),
            sampleRepository.observeControllableRatioLastNDays(days, domainId),
            sampleRepository.observeHighestEmotionLastNDays(days, domainId),
            sampleRepository.observeTopControllableErrorTypeLastNDays(days, domainId)
        ) { count, top, ratio, emo, suggested ->
            WeeklyStats(count, top, ratio, emo, suggested)
        }
    }

    val uiState: StateFlow<WeeklyUiState> = combine(
        statsFlow,
        daysState,
        domainFilterState,
        domainRepository.observeVisible()
    ) { stats, days, filter, domains ->
        WeeklyUiState(
            days = days,
            domainFilter = filter,
            sampleCount = stats.sampleCount,
            topErrors = stats.topErrors,
            controllableRatio = stats.controllableRatio,
            highestEmotion = stats.highestEmotion,
            suggestedError = stats.suggestedError,
            availableDomains = domains
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WeeklyUiState()
    )

    /** F6:切时间范围(7/14/30 天)。 */
    fun selectDays(days: Int) {
        daysState.value = days
    }

    /** F7:切领域(全部或某个可见领域)。 */
    fun selectDomain(filter: DomainFilter) {
        domainFilterState.value = filter
    }

    /** 五项原始聚合数据,内部组装用。 */
    private data class WeeklyStats(
        val sampleCount: Int,
        val topErrors: List<ErrorTypeCount>,
        val controllableRatio: ControllableRatio?,
        val highestEmotion: SampleWithErrorType?,
        val suggestedError: ErrorTypeCount?
    )

    class Factory(
        private val sampleRepository: SampleRepository,
        private val domainRepository: DomainRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WeeklyViewModel(sampleRepository, domainRepository) as T
    }
}
