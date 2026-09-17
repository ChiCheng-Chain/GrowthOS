package com.growthos.app.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.growthos.app.GrowthOSApp
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.PracticeSession
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.PracticeSessionRepository
import com.growthos.app.ui.components.LedgerRule
import com.growthos.app.ui.components.SectionCard
import com.growthos.app.util.TimeUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 编辑页事件:保存/删除成功触发返回(BR-5)。 */
sealed interface PracticeEditEvent {
    data object Saved : PracticeEditEvent
    data object Deleted : PracticeEditEvent
}

/**
 * 补录/编辑页状态(设计 D7/D12):领域单选 + 时间输入 + 备注;
 * practiceId=null 为补录(日期默认今天),非空为编辑(getById 预填)。
 */
data class PracticeEditUiState(
    val domains: List<Domain> = emptyList(),
    val isNew: Boolean = true,
    val selectedDomainId: Long? = null,
    val timeInput: PracticeTimeInput? = null,
    val note: String = "",
    val overlapWarning: Boolean = false
) {
    val canSave: Boolean
        get() = selectedDomainId != null && timeInput != null && timeInput.toEpochRange() != null
}

class PracticeEditViewModel(
    private val practiceRepository: PracticeSessionRepository,
    domainRepository: DomainRepository,
    private val practiceId: Long?,
    private val now: () -> Long = TimeUtil::nowMillis
) : ViewModel() {

    private val selectedDomainId = MutableStateFlow<Long?>(null)
    private val timeInput = MutableStateFlow<PracticeTimeInput?>(null)
    private val note = MutableStateFlow("")
    private val overlapWarning = MutableStateFlow(false)

    private val _events = MutableSharedFlow<PracticeEditEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PracticeEditEvent> = _events

    val uiState: StateFlow<PracticeEditUiState> = kotlinx.coroutines.flow.combine(
        domainRepository.observeVisible(),
        selectedDomainId,
        timeInput,
        note,
        overlapWarning
    ) { domains, domainId, input, noteText, warning ->
        PracticeEditUiState(
            domains = domains,
            isNew = practiceId == null,
            selectedDomainId = domainId,
            timeInput = input,
            note = noteText,
            overlapWarning = warning
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PracticeEditUiState(timeInput = defaultInput())
    )

    init {
        viewModelScope.launch {
            if (practiceId == null) {
                // 补录:日期默认今天,无预选领域
                timeInput.value = PracticeTimeInput(
                    dayStartMillis = TimeUtil.startOfTodayMillis(),
                    hourOfDay = 8,
                    durationMinutes = 30
                )
            } else {
                val session = practiceRepository.getById(practiceId) ?: return@launch
                selectedDomainId.value = session.domainId
                timeInput.value = session.endedAt?.let {
                    PracticeTimeInput.from(session.startedAt, it)
                } ?: PracticeTimeInput.from(session.startedAt, now())
                note.value = session.note ?: ""
            }
        }
    }

    fun selectDomain(id: Long) {
        selectedDomainId.value = id
        overlapWarning.value = false
    }

    fun updateTimeInput(input: PracticeTimeInput) {
        timeInput.value = input
        overlapWarning.value = false
    }

    fun updateNote(value: String) {
        note.value = value
    }

    /** 保存(BR-7):重叠被 Repository 拒绝 → 置警示;成功发事件。 */
    fun save() {
        val domainId = selectedDomainId.value ?: return
        val input = timeInput.value?.toEpochRange() ?: return
        viewModelScope.launch {
            val ok = practiceRepository.save(
                id = practiceId,
                domainId = domainId,
                startedAt = input.first,
                endedAt = input.second,
                note = note.value.takeIf { it.isNotBlank() },
                createdAt = now()
            )
            if (ok) _events.tryEmit(PracticeEditEvent.Saved)
            else overlapWarning.value = true
        }
    }

    fun delete() {
        val id = practiceId ?: return
        viewModelScope.launch {
            practiceRepository.delete(id)
            _events.tryEmit(PracticeEditEvent.Deleted)
        }
    }

    private fun defaultInput(): PracticeTimeInput =
        PracticeTimeInput(
            dayStartMillis = TimeUtil.startOfTodayMillis(),
            hourOfDay = 8,
            durationMinutes = 30
        )

    class Factory(
        private val practiceRepository: PracticeSessionRepository,
        private val domainRepository: DomainRepository,
        private val practiceId: Long?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PracticeEditViewModel(practiceRepository, domainRepository, practiceId) as T
    }
}

/**
 * 补录/编辑页(BR-5/UI-Q4):改领域/起点/时长/备注,底部红色删除沿用样本范式(B12)。
 */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun PracticeEditScreen(
    practiceId: Long?,
    onBack: () -> Unit
) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val vm: PracticeEditViewModel = viewModel(
        key = "practice_edit_$practiceId",
        factory = PracticeEditViewModel.Factory(
            container.practiceSessionRepository,
            container.domainRepository,
            practiceId
        )
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                PracticeEditEvent.Saved, PracticeEditEvent.Deleted -> onBack()
            }
        }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isNew) "补录练习" else "编辑练习",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. 领域(必填,chips 单选)
            SectionCard {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "领域",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        state.domains.take(3).forEach { domain ->
                            DomainChip(
                                name = domain.name,
                                selected = domain.id == state.selectedDomainId,
                                onClick = { vm.selectDomain(domain.id) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                    if (state.domains.size > 3) {
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            state.domains.drop(3).forEach { domain ->
                                DomainChip(
                                    name = domain.name,
                                    selected = domain.id == state.selectedDomainId,
                                    onClick = { vm.selectDomain(domain.id) },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // 2. 时间编辑器(起点小时+时长,日期行)
            SectionCard {
                Column(Modifier.padding(20.dp)) {
                    DayPickerRow(
                        selectedDay = state.timeInput?.dayStartMillis,
                        onSelectDay = { day ->
                            state.timeInput?.let { vm.updateTimeInput(it.copy(dayStartMillis = day)) }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    state.timeInput?.let { input ->
                        PracticeTimeEditor(
                            input = input,
                            onInputChanged = vm::updateTimeInput
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // 3. 备注(选填)
            SectionCard {
                Column(Modifier.padding(20.dp)) {
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = vm::updateNote,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("备注(选填)") },
                        singleLine = true
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (state.overlapWarning) {
                Text(
                    "与进行中的练习时间重叠,请调整起点或时长。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = vm::save,
                enabled = state.canSave,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
            ) { Text(if (state.isNew) "补录" else "保存") }

            // 删除(BR-5/B12):仅编辑态,底部红色 + 确认
            if (!state.isNew) {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Text(
                        "删除这条练习",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clickable { showDeleteConfirm = true }
                            .padding(12.dp)
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除练习") },
            text = { Text("删除这条练习?不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.delete()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DomainChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        name,
        modifier = modifier
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 日期选择(BR-4/AC-04:日期自由可选):今天/昨天/前天快捷 chips +「更早…」弹完整日历
 * (M3 DatePickerDialog,compose-bom 自带,设计 D7)。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DayPickerRow(selectedDay: Long?, onSelectDay: (Long) -> Unit) {
    val today = TimeUtil.startOfTodayMillis()
    val options = listOf(
        "今天" to today,
        "昨天" to today - 86_400_000L,
        "前天" to today - 2 * 86_400_000L
    )
    var showDatePicker by remember { mutableStateOf(false) }
    val dayFormatter = remember { SimpleDateFormat("M月d日", Locale.getDefault()) }
    val isQuickDay = options.any { it.second == selectedDay }

    Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        options.forEach { (label, day) ->
            val selected = day == selectedDay
            Text(
                label,
                modifier = Modifier
                    .clickable { onSelectDay(day) }
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 「更早…」:选中日不在快捷行时显示具体日期
        Text(
            if (selectedDay != null && !isQuickDay) dayFormatter.format(Date(selectedDay)) else "更早…",
            modifier = Modifier
                .clickable { showDatePicker = true }
                .background(
                    if (selectedDay != null && !isQuickDay) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.small
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selectedDay != null && !isQuickDay) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDay ?: today
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { pickedUtc ->
                        // DatePicker 用 UTC 日界,转本地时区日 0 点(对齐 TimeUtil 契约)
                        val localDay = TimeUtil.startOfDayMillis(
                            pickedUtc + TimeZone.getDefault().getOffset(pickedUtc).toLong()
                        )
                        onSelectDay(localDay)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
