package com.growthos.app.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.local.SelectedDomainStore
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.data.local.entity.Training
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.ErrorTypeRepository
import com.growthos.app.data.repository.TrainingRepository
import com.growthos.app.domain.model.TrainingStatus
import com.growthos.app.util.TimeUtil
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 训练项编辑页状态(二期阶段 5 / 设计 §状态设计)。
 *
 * 只做新建(D4 不做编辑)。预填:[errorTypeId] 从周复盘 F5/F2 入口带入(可改),
 * [domainId] 取 SelectedDomainStore 当前领域(无则空,用户选)。
 * startedAt = now 自动,不暴露给用户(D6)。
 */
data class TrainingEditUiState(
    val domains: List<Domain> = emptyList(),
    val errorTypes: List<ErrorType> = emptyList(),
    val form: TrainingForm = TrainingForm()
) {
    val canSave: Boolean get() = form.isValid
}

/** 训练项表单(对齐需求 F1)。必填:领域 / 错误类型 / 训练目标;验收标准与备注可选。 */
data class TrainingForm(
    val domainId: Long? = null,
    val errorTypeId: Long? = null,
    val goal: String = "",
    val acceptanceCriteria: String = "",
    val note: String = ""
) {
    val isValid: Boolean get() =
        domainId != null && errorTypeId != null && goal.isNotBlank()
}

/** TrainingEditViewModel 对 UI 发出的一次性事件(保存完成 → 返回)。 */
sealed interface TrainingEditEvent {
    data object Saved : TrainingEditEvent
}

class TrainingEditViewModel(
    private val trainingRepository: TrainingRepository,
    private val errorTypeRepository: ErrorTypeRepository,
    private val domainRepository: DomainRepository,
    private val selectedStore: SelectedDomainStore,
    private val prefillErrorTypeId: Long?,   // 从 F5/F2 入口带入,null = 不预填
    private val now: () -> Long = TimeUtil::nowMillis
) : ViewModel() {

    private val formState = MutableStateFlow(TrainingForm())

    private val _events = MutableSharedFlow<TrainingEditEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<TrainingEditEvent> = _events.asSharedFlow()

    val uiState: StateFlow<TrainingEditUiState> = combine(
        domainRepository.observeVisible(),
        errorTypeRepository.observeAll(),
        selectedStore.flow,
        formState
    ) { domains, errorTypes, selectedId, form ->
        // 预填:errorTypeId(入口带入,仅初始一次);domainId(当前选中领域,form 未设时填)。
        if (form.errorTypeId == null && prefillErrorTypeId != null &&
            errorTypes.any { it.id == prefillErrorTypeId }
        ) {
            formState.value = form.copy(errorTypeId = prefillErrorTypeId)
        }
        if (form.domainId == null && selectedId != null &&
            domains.any { it.id == selectedId }
        ) {
            formState.value = formState.value.copy(domainId = selectedId)
        }
        TrainingEditUiState(domains, errorTypes, formState.value)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TrainingEditUiState()
    )

    // ---------- 表单字段更新 ----------

    fun updateDomain(id: Long) { formState.value = formState.value.copy(domainId = id) }
    fun updateErrorType(id: Long) { formState.value = formState.value.copy(errorTypeId = id) }
    fun updateGoal(v: String) { formState.value = formState.value.copy(goal = v) }
    fun updateAcceptanceCriteria(v: String) { formState.value = formState.value.copy(acceptanceCriteria = v) }
    fun updateNote(v: String) { formState.value = formState.value.copy(note = v) }

    /** 新建错误类型并立即选中(复用 getOrCreate,重名复用不报错)。 */
    fun createErrorType(name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        val id = errorTypeRepository.getOrCreate(trimmed)
        formState.value = formState.value.copy(errorTypeId = id)
    }

    // ---------- 保存 ----------

    /** 保存新建训练项。form 无效时直接返回(UI 已 disabled,此为兜底)。 */
    fun save() = viewModelScope.launch {
        val form = formState.value
        if (!form.isValid) return@launch
        trainingRepository.create(
            Training(
                domainId = form.domainId!!,
                errorTypeId = form.errorTypeId!!,
                goal = form.goal.trim(),
                acceptanceCriteria = form.acceptanceCriteria.trim().ifBlank { null },
                startedAt = now(),
                endedAt = null,
                status = TrainingStatus.IN_PROGRESS,
                note = form.note.trim().ifBlank { null }
            )
        )
        _events.tryEmit(TrainingEditEvent.Saved)
    }

    class Factory(
        private val trainingRepository: TrainingRepository,
        private val errorTypeRepository: ErrorTypeRepository,
        private val domainRepository: DomainRepository,
        private val store: SelectedDomainStore,
        private val prefillErrorTypeId: Long?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TrainingEditViewModel(
                trainingRepository, errorTypeRepository, domainRepository, store, prefillErrorTypeId
            ) as T
    }
}
