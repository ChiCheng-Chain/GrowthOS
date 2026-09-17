package com.growthos.app.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.local.dao.PracticeDetail
import com.growthos.app.data.local.dao.PracticeDomainTotal
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.PracticeSessionRepository
import com.growthos.app.ui.weekly.DomainFilter
import com.growthos.app.util.DurationFormat
import com.growthos.app.util.TimeUtil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 看板一天的时长(按天趋势,VM 内存分桶,设计 D9③/D10)。 */
data class PracticeDailyTotal(
    val dayStartMillis: Long,
    val totalMillis: Long
)

/** 看板三区块聚合(D9/D11):总时长+场均、领域分布、按天趋势。 */
data class PracticeBoardUiState(
    val days: Int = 7,
    val domainFilter: DomainFilter = DomainFilter.All,
    val totalText: String = "0分",
    val averageText: String = "0分",
    val count: Int = 0,
    val domainTotals: List<PracticeDomainTotal> = emptyList(),
    val dailyTotals: List<PracticeDailyTotal> = emptyList()
)

/**
 * 看板子页状态机(设计 D11):独立 days/domainFilter,与周复盘互不共享(CE-004)。
 * 口径切换即时重算;domainId 由 DomainFilter 折叠成 0(全局)或具体 id(哨兵惯例)。
 */
class PracticeBoardViewModel(
    private val practiceRepository: PracticeSessionRepository,
    domainRepository: DomainRepository
) : ViewModel() {

    private val daysState = kotlinx.coroutines.flow.MutableStateFlow(7)
    private val domainFilterState = kotlinx.coroutines.flow.MutableStateFlow<DomainFilter>(DomainFilter.All)

    val domains: StateFlow<List<com.growthos.app.data.local.entity.Domain>> =
        domainRepository.observeVisible()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // 供弹层展示当前口径(不直接暴露 StateFlow 保持 UiState 单一来源)
    }

    val uiState: StateFlow<PracticeBoardUiState> = combine(
        daysState,
        domainFilterState,
        practiceRepository.run {
            // 以下三条流的窗口都依赖 days+domainFilter,flatMapLatest 切口径
            combine(daysState, domainFilterState) { d, f -> d to f }.flatMapLatest { (days, filter) ->
                val domainId = when (filter) {
                    DomainFilter.All -> 0L
                    is DomainFilter.Single -> filter.domainId
                }
                val range = TimeUtil.lastNDaysRange(days)
                combine(
                    observeTotalAndCount(domainId, range.first, range.last),
                    observeByDomain(domainId, range.first, range.last),
                    observeWindowDetails(domainId, range.first, range.last)
                ) { total, byDomain, details ->
                    Triple(total, byDomain, details)
                }
            }
        }
    ) { days, filter, (total, byDomain, details) ->
        val dayGroups = details
            .groupBy { TimeUtil.startOfDayMillis(it.startedAt) }
            .map { (day, items) ->
                PracticeDailyTotal(day, items.sumOf { it.endedAt - it.startedAt })
            }
            .sortedByDescending { it.dayStartMillis }
        PracticeBoardUiState(
            days = days,
            domainFilter = filter,
            totalText = DurationFormat.formatMillis(total.totalMillis),
            averageText = if (total.count > 0) {
                DurationFormat.formatMillis(total.totalMillis / total.count)
            } else "0分",
            count = total.count,
            domainTotals = byDomain,
            dailyTotals = dayGroups
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PracticeBoardUiState())

    fun selectDays(days: Int) {
        daysState.value = days
    }

    fun selectDomain(filter: DomainFilter) {
        domainFilterState.value = filter
    }

    class Factory(
        private val practiceRepository: PracticeSessionRepository,
        private val domainRepository: DomainRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PracticeBoardViewModel(practiceRepository, domainRepository) as T
    }
}
