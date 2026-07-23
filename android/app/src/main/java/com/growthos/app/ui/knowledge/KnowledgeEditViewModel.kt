package com.growthos.app.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.Knowledge
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.KnowledgeRepository
import com.growthos.app.domain.model.KnowledgeType
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
 * 知识编辑页状态。对齐 [com.growthos.app.ui.principle.PrincipleEditViewModel] 范式。
 *
 * 新建/编辑共用:knowledgeId 非正=新建,正=编辑预填(保留原 createdAt)。
 * 内容必填;类型(经验/待办)必选;领域可选。比 Principle 简单——不关联错误类型。
 */
data class KnowledgeEditUiState(
    val domains: List<Domain> = emptyList(),
    val form: KnowledgeForm = KnowledgeForm(),
    val isEditing: Boolean = false
) {
    val canSave: Boolean get() = form.isValid
}

data class KnowledgeForm(
    val content: String = "",
    val type: KnowledgeType = KnowledgeType.EXPERIENCE,
    val domainId: Long? = null
) {
    val isValid: Boolean get() = content.isNotBlank()
}

sealed interface KnowledgeEditEvent {
    data object Saved : KnowledgeEditEvent
    data object Deleted : KnowledgeEditEvent
}

class KnowledgeEditViewModel(
    private val knowledgeRepository: KnowledgeRepository,
    private val domainRepository: DomainRepository,
    private val knowledgeId: Long?,
    private val now: () -> Long = TimeUtil::nowMillis
) : ViewModel() {

    private val formState = MutableStateFlow(KnowledgeForm())
    private val isEditingState = MutableStateFlow(false)
    private var existing: Knowledge? = null

    private val _events = MutableSharedFlow<KnowledgeEditEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<KnowledgeEditEvent> = _events.asSharedFlow()

    val uiState: StateFlow<KnowledgeEditUiState> = combine(
        domainRepository.observeVisible(),
        formState,
        isEditingState
    ) { domains, form, editing ->
        KnowledgeEditUiState(domains, form, editing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = KnowledgeEditUiState()
    )

    init {
        if (knowledgeId != null && knowledgeId > 0) {
            viewModelScope.launch {
                knowledgeRepository.getById(knowledgeId)?.let { k ->
                    existing = k
                    isEditingState.value = true
                    formState.value = KnowledgeForm(
                        content = k.content,
                        type = k.type,
                        domainId = k.domainId
                    )
                }
            }
        }
    }

    fun updateContent(v: String) { formState.value = formState.value.copy(content = v) }
    fun updateType(t: KnowledgeType) { formState.value = formState.value.copy(type = t) }
    fun updateDomain(id: Long?) { formState.value = formState.value.copy(domainId = id) }

    fun save() = viewModelScope.launch {
        val form = formState.value
        if (!form.isValid) return@launch
        val existingKnowledge = existing
        if (isEditingState.value && existingKnowledge != null) {
            knowledgeRepository.update(
                existingKnowledge.copy(
                    content = form.content.trim(),
                    type = form.type,
                    domainId = form.domainId
                )
            )
        } else {
            knowledgeRepository.insert(
                Knowledge(
                    content = form.content.trim(),
                    type = form.type,
                    createdAt = now(),
                    domainId = form.domainId
                )
            )
        }
        _events.tryEmit(KnowledgeEditEvent.Saved)
    }

    fun delete() = viewModelScope.launch {
        existing?.let { knowledgeRepository.delete(it) }
        _events.tryEmit(KnowledgeEditEvent.Deleted)
    }

    class Factory(
        private val knowledgeRepository: KnowledgeRepository,
        private val domainRepository: DomainRepository,
        private val knowledgeId: Long?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            KnowledgeEditViewModel(knowledgeRepository, domainRepository, knowledgeId) as T
    }
}
