package com.growthos.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.local.SelectedDomainStore
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.data.local.entity.Sample
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.ErrorTypeRepository
import com.growthos.app.data.repository.SampleRepository
import com.growthos.app.domain.model.Attribution
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
 * 录入 / 编辑页状态(二期阶段 2 / 设计文档 §状态设计)。
 *
 * - [domains]:可见领域(chips),来自 DomainRepository.observeVisible()。
 * - [errorTypes]:全部已有错误类型(全局,跨领域复用),来自 ErrorTypeRepository.observeAll()。
 * - [form]:逐字段表单,MutableStateFlow 驱动。
 * - [isEditing]:true=编辑已有(预填),false=新建。
 * - [isNewErrorTypeDialogOpen]:新建错误类型底部对话框开关。
 */
data class SampleEditUiState(
    val domains: List<Domain> = emptyList(),
    val errorTypes: List<ErrorType> = emptyList(),
    val form: SampleForm = SampleForm(),
    val isEditing: Boolean = false,
    val isNewErrorTypeDialogOpen: Boolean = false
) {
    /** 当前选中的领域对象(form.domainId 对应)。 */
    val selectedDomain: Domain? get() = domains.firstOrNull { it.id == form.domainId }
}

/**
 * 录入表单(对齐需求 F2 七字段)。
 * 必填:领域 / 结果 / 描述 / 错误类型 / 归因 / 复盘;情绪可选。
 */
data class SampleForm(
    val domainId: Long? = null,
    val result: String = "",
    val description: String = "",
    val errorTypeId: Long? = null,
    val attribution: Attribution? = null,
    val emotionIntensity: Int? = null,
    val review: String = ""
) {
    /** 必填校验(对齐设计 D2 / 需求 F2)。情绪可选,不参与校验。 */
    val isValid: Boolean get() =
        domainId != null &&
            result.isNotBlank() &&
            description.isNotBlank() &&
            errorTypeId != null &&
            attribution != null &&
            review.isNotBlank()
}

/** SampleViewModel 对 UI 发出的一次性事件(保存 / 删除完成 → 返回)。 */
sealed interface SampleEvent {
    data object Saved : SampleEvent
    data object Deleted : SampleEvent
}

/**
 * 错误类型删除事件(阶段 7 / R-014 / 设计 §SampleViewModel 错误类型删除扩展)。
 *
 * - [Blocked]:被样本 / 训练项引用,不可删,UI 提示引用数。
 * - [ConfirmDelete]:未引用,UI 弹确认对话框;用户确认后调 [SampleViewModel.confirmDeleteErrorType]。
 * - [ErrorTypeDeleted]:已删除,chip 列表自动刷新;UI 据此关对话框。
 */
sealed interface ErrorTypeDeleteEvent {
    data class Blocked(val referenceCount: Int) : ErrorTypeDeleteEvent
    data class ConfirmDelete(val errorType: ErrorType) : ErrorTypeDeleteEvent
    data object ErrorTypeDeleted : ErrorTypeDeleteEvent
}

class SampleViewModel(
    private val sampleRepository: SampleRepository,
    private val errorTypeRepository: ErrorTypeRepository,
    private val domainRepository: DomainRepository,
    private val selectedStore: SelectedDomainStore,
    private val sampleId: Long?,           // null / 非正 = 新建;正 = 编辑
    private val now: () -> Long = TimeUtil::nowMillis
) : ViewModel() {

    private val formState = MutableStateFlow(SampleForm())
    private val dialogState = MutableStateFlow(false)
    private val isEditingState = MutableStateFlow(false)

    private val _events = MutableSharedFlow<SampleEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SampleEvent> = _events.asSharedFlow()

    private val _errorTypeDeleteEvents = MutableSharedFlow<ErrorTypeDeleteEvent>(extraBufferCapacity = 1)
    val errorTypeDeleteEvents: SharedFlow<ErrorTypeDeleteEvent> = _errorTypeDeleteEvents.asSharedFlow()

    val uiState: StateFlow<SampleEditUiState> = combine(
        domainRepository.observeVisible(),
        errorTypeRepository.observeAll(),
        selectedStore.flow,
        formState,
        combine(dialogState, isEditingState) { dialog, editing -> dialog to editing }
    ) { domains, errorTypes, selectedId, form, (dialog, editing) ->
        // 新建态默认领域:form.domainId 未设时,用当前选中领域;编辑态不覆盖预填值。
        if (form.domainId == null && !editing && selectedId != null &&
            domains.any { it.id == selectedId }
        ) {
            formState.value = form.copy(domainId = selectedId)
        }
        SampleEditUiState(
            domains = domains,
            errorTypes = errorTypes,
            form = form,
            isEditing = editing,
            isNewErrorTypeDialogOpen = dialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SampleEditUiState()
    )

    init {
        // 编辑态:预填表单。sampleId 非正视为新建。
        if (sampleId != null && sampleId > 0) {
            viewModelScope.launch {
                sampleRepository.getById(sampleId)?.let { sample ->
                    isEditingState.value = true
                    formState.value = SampleForm(
                        domainId = sample.domainId,
                        result = sample.result,
                        description = sample.description,
                        errorTypeId = sample.errorTypeId,
                        attribution = sample.attribution,
                        emotionIntensity = sample.emotionIntensity,
                        review = sample.review
                    )
                }
            }
        }
    }

    // ---------- 表单字段更新 ----------

    fun updateDomain(id: Long) { formState.value = formState.value.copy(domainId = id) }
    fun updateResult(v: String) { formState.value = formState.value.copy(result = v) }
    fun updateDescription(v: String) { formState.value = formState.value.copy(description = v) }
    fun updateErrorType(id: Long) { formState.value = formState.value.copy(errorTypeId = id) }
    fun updateAttribution(a: Attribution) { formState.value = formState.value.copy(attribution = a) }

    /** 情绪点选:点已选档清空回 null(D6);否则设 1~5。 */
    fun updateEmotion(value: Int) {
        val cur = formState.value.emotionIntensity
        formState.value = formState.value.copy(emotionIntensity = if (cur == value) null else value)
    }

    fun updateReview(v: String) { formState.value = formState.value.copy(review = v) }

    // ---------- 错误类型新建 ----------

    fun openNewErrorTypeDialog() { dialogState.value = true }
    fun dismissNewErrorTypeDialog() { dialogState.value = false }

    /** 新建并立即选中(D4)。走 getOrCreate,重名复用不报错。 */
    fun createErrorType(name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        val id = errorTypeRepository.getOrCreate(trimmed)
        formState.value = formState.value.copy(errorTypeId = id)
        dialogState.value = false
    }

    // ---------- 错误类型删除(R-014) ----------

    /**
     * 长按 chip 触发:查 referenceCount(阶段 0 已建)。
     * >0 emit [ErrorTypeDeleteEvent.Blocked](UI 提示引用数,不删);
     * =0 emit [ErrorTypeDeleteEvent.ConfirmDelete](UI 弹确认,确认后调 [confirmDeleteErrorType])。
     */
    fun requestDeleteErrorType(errorType: ErrorType) = viewModelScope.launch {
        val count = errorTypeRepository.referenceCount(errorType.id)
        if (count > 0) {
            _errorTypeDeleteEvents.tryEmit(ErrorTypeDeleteEvent.Blocked(count))
        } else {
            _errorTypeDeleteEvents.tryEmit(ErrorTypeDeleteEvent.ConfirmDelete(errorType))
        }
    }

    /** 确认删除:调 delete,若删的是当前选中则清空 form.errorTypeId。 */
    fun confirmDeleteErrorType(errorType: ErrorType) = viewModelScope.launch {
        errorTypeRepository.delete(errorType.id)
        if (formState.value.errorTypeId == errorType.id) {
            formState.value = formState.value.copy(errorTypeId = null)
        }
        _errorTypeDeleteEvents.tryEmit(ErrorTypeDeleteEvent.ErrorTypeDeleted)
    }

    // ---------- 保存 / 删除 ----------

    /** 保存(新建 / 编辑)。form 无效时直接返回(UI 已 disabled,此为兜底)。 */
    fun save() = viewModelScope.launch {
        val form = formState.value
        if (!form.isValid) return@launch
        if (isEditingState.value && sampleId != null && sampleId > 0) {
            // 编辑:保留原 recordedAt,其余用 form(D7)。
            val existing = sampleRepository.getById(sampleId)
            if (existing != null) {
                sampleRepository.update(
                    existing.copy(
                        domainId = form.domainId!!,
                        result = form.result.trim(),
                        description = form.description.trim(),
                        errorTypeId = form.errorTypeId!!,
                        attribution = form.attribution!!,
                        emotionIntensity = form.emotionIntensity,
                        review = form.review.trim()
                    )
                )
            }
        } else {
            // 新建:recordedAt = now(D7)。
            sampleRepository.insert(
                Sample(
                    domainId = form.domainId!!,
                    recordedAt = now(),
                    result = form.result.trim(),
                    description = form.description.trim(),
                    errorTypeId = form.errorTypeId!!,
                    attribution = form.attribution!!,
                    emotionIntensity = form.emotionIntensity,
                    review = form.review.trim()
                )
            )
        }
        _events.tryEmit(SampleEvent.Saved)
    }

    /** 删除当前编辑样本(R-014)。仅编辑态可用。 */
    fun delete() = viewModelScope.launch {
        if (isEditingState.value && sampleId != null && sampleId > 0) {
            sampleRepository.getById(sampleId)?.let { sampleRepository.delete(it) }
            _events.tryEmit(SampleEvent.Deleted)
        }
    }

    class Factory(
        private val sampleRepository: SampleRepository,
        private val errorTypeRepository: ErrorTypeRepository,
        private val domainRepository: DomainRepository,
        private val store: SelectedDomainStore,
        private val sampleId: Long?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SampleViewModel(
                sampleRepository, errorTypeRepository, domainRepository, store, sampleId
            ) as T
    }
}
