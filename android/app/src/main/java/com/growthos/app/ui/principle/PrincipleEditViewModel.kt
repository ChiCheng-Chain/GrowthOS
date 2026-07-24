package com.growthos.app.ui.principle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.data.local.entity.Principle
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.ErrorTypeRepository
import com.growthos.app.data.repository.PrincipleRepository
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
 * 原则编辑页状态(二期阶段 6 / 设计 §状态设计)。
 *
 * 新建/编辑共用一页(D2):principleId 非正=新建,正=编辑预填(保留原 createdAt)。
 * 内容必填;领域/错误类型 chips 可选;训练项/样本靠入口预填,不暴露选择器(D3)。
 */
data class PrincipleEditUiState(
    val domains: List<Domain> = emptyList(),
    val errorTypes: List<ErrorType> = emptyList(),
    val form: PrincipleForm = PrincipleForm(),
    val isEditing: Boolean = false
) {
    val canSave: Boolean get() = form.isValid
}

/** 原则表单(对齐需求 F1)。内容必填;四关联全可选。 */
data class PrincipleForm(
    val content: String = "",
    val domainId: Long? = null,
    val errorTypeId: Long? = null,
    val trainingId: Long? = null,
    val sampleId: Long? = null
) {
    val isValid: Boolean get() = content.isNotBlank()
}

/** PrincipleEditViewModel 对 UI 发出的一次性事件(保存 / 删除完成 → 返回)。 */
sealed interface PrincipleEditEvent {
    data object Saved : PrincipleEditEvent
    data object Deleted : PrincipleEditEvent
}

class PrincipleEditViewModel(
    private val principleRepository: PrincipleRepository,
    private val domainRepository: DomainRepository,
    private val errorTypeRepository: ErrorTypeRepository,
    private val principleId: Long?,              // null/非正=新建;正=编辑
    private val prefillTrainingId: Long?,        // 预填入口带入(D3,本阶段暂未建)
    private val prefillSampleId: Long?,          // 预填入口带入(D3,本阶段暂未建)
    private val prefillDomainId: Long? = null,          // 领域页空状态新建入口带入
    private val now: () -> Long = TimeUtil::nowMillis
) : ViewModel() {

    private val formState = MutableStateFlow(PrincipleForm())
    private val isEditingState = MutableStateFlow(false)
    private var existing: Principle? = null      // 编辑态保留原 createdAt

    private val _events = MutableSharedFlow<PrincipleEditEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<PrincipleEditEvent> = _events.asSharedFlow()

    val uiState: StateFlow<PrincipleEditUiState> = combine(
        domainRepository.observeVisible(),
        errorTypeRepository.observeAll(),
        formState,
        isEditingState
    ) { domains, errorTypes, form, editing ->
        PrincipleEditUiState(domains, errorTypes, form, editing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PrincipleEditUiState()
    )

    init {
        if (principleId != null && principleId > 0) {
            viewModelScope.launch {
                principleRepository.getById(principleId)?.let { p ->
                    existing = p
                    isEditingState.value = true
                    formState.value = PrincipleForm(
                        content = p.content,
                        domainId = p.domainId,
                        errorTypeId = p.errorTypeId,
                        trainingId = p.trainingId,
                        sampleId = p.sampleId
                    )
                }
            }
        } else {
            // 新建态:预填入口带入的训练项/样本/领域(D3)
            formState.value = PrincipleForm(
                trainingId = prefillTrainingId,
                sampleId = prefillSampleId,
                domainId = prefillDomainId?.takeIf { it > 0 }
            )
        }
    }

    // ---------- 表单字段更新 ----------

    fun updateContent(v: String) { formState.value = formState.value.copy(content = v) }
    fun updateDomain(id: Long?) { formState.value = formState.value.copy(domainId = id) }
    fun updateErrorType(id: Long?) { formState.value = formState.value.copy(errorTypeId = id) }

    // ---------- 保存 / 删除 ----------

    /** 保存(新建 / 编辑)。form 无效时直接返回(UI 已 disabled,此为兜底)。 */
    fun save() = viewModelScope.launch {
        val form = formState.value
        if (!form.isValid) return@launch
        val existingPrinciple = existing
        if (isEditingState.value && existingPrinciple != null) {
            // 编辑:保留原 id/createdAt,其余用 form(D2)。
            principleRepository.update(
                existingPrinciple.copy(
                    content = form.content.trim(),
                    domainId = form.domainId,
                    errorTypeId = form.errorTypeId,
                    trainingId = form.trainingId,
                    sampleId = form.sampleId
                )
            )
        } else {
            // 新建:用注入的 now 控制 createdAt(对齐 SampleViewModel 范式,便于测试)。
            principleRepository.insert(
                Principle(
                    content = form.content.trim(),
                    createdAt = now(),
                    domainId = form.domainId,
                    errorTypeId = form.errorTypeId,
                    trainingId = form.trainingId,
                    sampleId = form.sampleId
                )
            )
        }
        _events.tryEmit(PrincipleEditEvent.Saved)
    }

    /** 删除当前编辑原则(R-014)。仅编辑态可用。 */
    fun delete() = viewModelScope.launch {
        existing?.let { principleRepository.delete(it) }
        _events.tryEmit(PrincipleEditEvent.Deleted)
    }

    class Factory(
        private val principleRepository: PrincipleRepository,
        private val domainRepository: DomainRepository,
        private val errorTypeRepository: ErrorTypeRepository,
        private val principleId: Long?,
        private val prefillTrainingId: Long?,
        private val prefillSampleId: Long?,
        private val prefillDomainId: Long?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PrincipleEditViewModel(
                principleRepository, domainRepository, errorTypeRepository,
                principleId, prefillTrainingId, prefillSampleId, prefillDomainId
            ) as T
    }
}
