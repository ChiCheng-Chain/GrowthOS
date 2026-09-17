package com.growthos.app.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.local.LastPracticeDomainStore
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.PracticeSession
import com.growthos.app.data.local.relation.PracticeSessionWithDomainName
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.PracticeSessionRepository
import com.growthos.app.util.DurationFormat
import com.growthos.app.util.TimeUtil
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 练习 Tab 事件(动线收口预留)。 */
sealed interface PracticeEvent {
    data object Started : PracticeEvent
    data object Finished : PracticeEvent
}

/**
 * 练习 Tab 状态机(feature 2026-09-17 / 设计 D4/D5/D12)。
 *
 * - [active]:进行中一行(endedAt=null,至多一条,不变式在 Repository);
 *   进程被杀后 Flow 重查自然恢复(BR-6)。
 * - [dayGroups]:按天分组的已完成练习列表,日期倒序(内存分桶,量级个人几百条,D12)。
 * - [todayTotalText]:今日时长小结(BR-9 文案)。
 * - [lastDomainId]:上次计时的领域(DataStore,默认选中用)。
 */
class PracticeViewModel(
    private val practiceRepository: PracticeSessionRepository,
    domainRepository: DomainRepository,
    private val lastPracticeDomainStore: LastPracticeDomainStore,
    private val now: () -> Long = TimeUtil::nowMillis
) : ViewModel() {

    val domains: StateFlow<List<Domain>> = domainRepository.observeVisible()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 上次计时的领域 id(null=没记过)。 */
    val lastDomainId: StateFlow<Long?> = lastPracticeDomainStore.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 进行中行;null=空闲。 */
    val active: StateFlow<PracticeSession?> = practiceRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val groupedAndToday: StateFlow<Pair<List<PracticeDayGroup>, Long>> =
        combine(
            practiceRepository.observeAllWithDomain(),
            practiceRepository.observeTotalAndCount(
                0, TimeUtil.startOfTodayMillis(), TimeUtil.startOfNextDayMillis()
            )
        ) { rows, today ->
            rows.toDayGroups() to today.totalMillis
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<PracticeDayGroup>() to 0L)

    val dayGroups: StateFlow<List<PracticeDayGroup>> = groupedAndToday
        .map { it.first }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val todayTotalText: StateFlow<String> = groupedAndToday
        .map { it.second }
        .map { DurationFormat.formatMillis(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "0分")

    // ---------- 动线动作(D3/D4) ----------

    private val _events = MutableSharedFlow<PracticeEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PracticeEvent> = _events

    /** 开始计时:选定领域即落一行进行中;DataStore 记忆领域(UI-Q1)。已有进行中被 Repository 拒绝。 */
    fun start(domainId: Long) {
        viewModelScope.launch {
            practiceRepository.start(domainId, now())
            lastPracticeDomainStore.set(domainId)
        }
    }

    /** 结束计时:endedAt=真实当前时刻(BR-6),note 选填(UI-Q3)。 */
    fun finish(note: String?) {
        viewModelScope.launch {
            practiceRepository.getActive()?.let { active ->
                practiceRepository.finish(active.id, now(), note?.takeIf { it.isNotBlank() })
            }
        }
    }

    class Factory(
        private val practiceRepository: PracticeSessionRepository,
        private val domainRepository: DomainRepository,
        private val lastPracticeDomainStore: LastPracticeDomainStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PracticeViewModel(practiceRepository, domainRepository, lastPracticeDomainStore) as T
    }
}

/** 一天的练习组(组头含当日合计)。 */
data class PracticeDayGroup(
    val dayStartMillis: Long,
    val rows: List<PracticeSessionWithDomainName>,
    val totalMillis: Long
)

/** 按天分桶:本地时区日界(TimeUtil 契约),日期倒序,同日内按开始时间倒序。 */
fun List<PracticeSessionWithDomainName>.toDayGroups(): List<PracticeDayGroup> {
    return filter { it.session.endedAt != null }
        .groupBy { TimeUtil.startOfDayMillis(it.session.startedAt) }
        .map { (day, rows) ->
            PracticeDayGroup(
                dayStartMillis = day,
                rows = rows.sortedByDescending { it.session.startedAt },
                totalMillis = rows.sumOf { it.session.endedAt!! - it.session.startedAt }
            )
        }
        .sortedByDescending { it.dayStartMillis }
}
