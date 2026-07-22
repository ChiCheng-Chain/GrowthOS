package com.growthos.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.growthos.app.GrowthOSApp
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.domain.model.Attribution
import com.growthos.app.ui.components.LedgerRule
import com.growthos.app.ui.components.PageHeader
import com.growthos.app.ui.theme.GrowthOSTheme
import kotlinx.coroutines.flow.collect

/**
 * 录入 / 编辑页(二期阶段 2 / 设计 §UI 组件)。
 *
 * 壳层:取 [SampleViewModel](从 AppContainer 注入),收集 [SampleEvent] 触发返回。
 * 内容层 [SampleEditContent] 纯展示,便于 @Preview。
 */
@Composable
fun SampleEditScreen(
    sampleId: Long?,
    onBack: () -> Unit,
    onNavigateToDomains: () -> Unit
) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val vm: SampleViewModel = viewModel(
        factory = SampleViewModel.Factory(
            container.sampleRepository,
            container.errorTypeRepository,
            container.domainRepository,
            container.selectedDomainStore,
            sampleId
        )
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    // 阶段 7:错误类型删除事件(被引用拦截 / 弹确认 / 已删除)。
    // 用 remember 暂存「待确认删除」的错误类型,供确认对话框展示。
    var pendingDelete by remember { mutableStateOf<ErrorType?>(null) }
    var blockedCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                SampleEvent.Saved, SampleEvent.Deleted -> onBack()
            }
        }
    }
    LaunchedEffect(vm) {
        vm.errorTypeDeleteEvents.collect { event ->
            when (event) {
                is ErrorTypeDeleteEvent.Blocked -> blockedCount = event.referenceCount
                is ErrorTypeDeleteEvent.ConfirmDelete -> pendingDelete = event.errorType
                ErrorTypeDeleteEvent.ErrorTypeDeleted -> pendingDelete = null
            }
        }
    }

    SampleEditContent(
        state = state,
        onUpdateDomain = vm::updateDomain,
        onUpdateResult = vm::updateResult,
        onUpdateDescription = vm::updateDescription,
        onUpdateErrorType = vm::updateErrorType,
        onUpdateAttribution = vm::updateAttribution,
        onUpdateEmotion = vm::updateEmotion,
        onUpdateReview = vm::updateReview,
        onOpenNewErrorType = vm::openNewErrorTypeDialog,
        onDismissNewErrorType = vm::dismissNewErrorTypeDialog,
        onCreateErrorType = vm::createErrorType,
        onLongClickErrorType = vm::requestDeleteErrorType,
        onSave = vm::save,
        onDelete = vm::delete,
        onBack = onBack,
        onNavigateToDomains = onNavigateToDomains,
        pendingDelete = pendingDelete,
        blockedCount = blockedCount,
        onConfirmDeleteErrorType = {
            vm.confirmDeleteErrorType(it)
        },
        onDismissDeleteErrorType = { pendingDelete = null },
        onDismissBlocked = { blockedCount = null }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
private fun SampleEditContent(
    state: SampleEditUiState,
    onUpdateDomain: (Long) -> Unit,
    onUpdateResult: (String) -> Unit,
    onUpdateDescription: (String) -> Unit,
    onUpdateErrorType: (Long) -> Unit,
    onUpdateAttribution: (Attribution) -> Unit,
    onUpdateEmotion: (Int) -> Unit,
    onUpdateReview: (String) -> Unit,
    onOpenNewErrorType: () -> Unit,
    onDismissNewErrorType: () -> Unit,
    onCreateErrorType: (String) -> Unit,
    onLongClickErrorType: (ErrorType) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    onNavigateToDomains: () -> Unit,
    pendingDelete: ErrorType?,
    blockedCount: Int?,
    onConfirmDeleteErrorType: (ErrorType) -> Unit,
    onDismissDeleteErrorType: () -> Unit,
    onDismissBlocked: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val form = state.form

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isEditing) "编辑样本" else "新建样本",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            // 无可见领域 → 引导先建领域(对接阶段 1 空状态)
            if (state.domains.isEmpty()) {
                NoDomainGuide(onNavigateToDomains = onNavigateToDomains)
                Spacer(Modifier.height(32.dp))
                return@Column
            }

            // 1. 领域 chips(默认选中当前)
            FieldLabel("领域")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.domains.forEach { domain ->
                    DomainPickChip(
                        name = domain.name,
                        selected = domain.id == form.domainId,
                        onClick = { onUpdateDomain(domain.id) }
                    )
                }
            }
            LedgerRule(modifier = Modifier.padding(top = 12.dp))

            // 2. 结果
            FieldLabel("结果")
            FormTextField(
                value = form.result,
                onValueChange = onUpdateResult,
                placeholder = "这次造成了什么后果或反馈",
                singleLine = true
            )
            LedgerRule(modifier = Modifier.padding(top = 12.dp))

            // 3. 一句话描述
            FieldLabel("一句话描述")
            FormTextField(
                value = form.description,
                onValueChange = onUpdateDescription,
                placeholder = "发生了什么",
                singleLine = false
            )
            LedgerRule(modifier = Modifier.padding(top = 12.dp))

            // 4. 错误类型(内联 + 新建)
            FieldLabel("错误类型")
            ErrorTypePickerInline(
                errorTypes = state.errorTypes,
                selectedId = form.errorTypeId,
                onSelect = onUpdateErrorType,
                onCreateNew = onCreateErrorType,
                isDialogOpen = state.isNewErrorTypeDialogOpen,
                onOpenDialog = onOpenNewErrorType,
                onDismissDialog = onDismissNewErrorType,
                onLongClickErrorType = onLongClickErrorType
            )
            LedgerRule(modifier = Modifier.padding(top = 12.dp))

            // 5. 归因(四选一)
            FieldLabel("归因")
            AttributionChips(
                selected = form.attribution,
                onSelect = onUpdateAttribution
            )
            LedgerRule(modifier = Modifier.padding(top = 12.dp))

            // 6. 情绪强度(可选)
            FieldLabel("情绪强度", required = false)
            EmotionSelector(
                value = form.emotionIntensity,
                onSelect = onUpdateEmotion
            )
            LedgerRule(modifier = Modifier.padding(top = 12.dp))

            // 7. 一句话复盘
            FieldLabel("一句话复盘")
            FormTextField(
                value = form.review,
                onValueChange = onUpdateReview,
                placeholder = "下次怎么做",
                singleLine = false
            )
            LedgerRule(modifier = Modifier.padding(top = 12.dp))

            // 保存
            Spacer(Modifier.height(20.dp))
            SaveButton(enabled = form.isValid, onSave = onSave)

            // 删除(仅编辑态)
            if (state.isEditing) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "删除这条样本",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除样本") },
            text = { Text("删除这条样本?不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    // 阶段 7(R-014):错误类型被引用 → 拦截提示(仅「知道了」)。
    blockedCount?.let { count ->
        AlertDialog(
            onDismissRequest = onDismissBlocked,
            title = { Text("无法删除") },
            text = { Text("$count 条样本 / 训练项在用,无法删除。") },
            confirmButton = {
                TextButton(onClick = onDismissBlocked) { Text("知道了") }
            }
        )
    }

    // 错误类型未引用 → 确认删除。
    pendingDelete?.let { et ->
        AlertDialog(
            onDismissRequest = onDismissDeleteErrorType,
            title = { Text("删除错误类型") },
            text = { Text("删除「${et.name}」?未引用时可直接删除。") },
            confirmButton = {
                TextButton(onClick = {
                    onConfirmDeleteErrorType(et)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteErrorType) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DomainPickChip(name: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.onBackground
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.background
    else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = name,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        modifier = Modifier
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    )
}

@Composable
private fun SaveButton(enabled: Boolean, onSave: () -> Unit) {
    Surface(
        onClick = { if (enabled) onSave() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        color = if (enabled) MaterialTheme.colorScheme.onBackground
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.background
        else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "保存",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun NoDomainGuide(onNavigateToDomains: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            "还没有可见领域",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "样本必须归属一个领域。先去领域页建一个,再回来记录。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            onClick = onNavigateToDomains,
            color = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.background
        ) {
            Text(
                "去领域页",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}

// ---------- Previews ----------

private val previewDomains = listOf(
    Domain(id = 1, name = "编程", createdAt = 0),
    Domain(id = 2, name = "酒馆战旗", createdAt = 1)
)
private val previewErrorTypes = listOf(
    ErrorType(id = 1, name = "边界条件遗漏", createdAt = 0),
    ErrorType(id = 2, name = "压力下急躁", createdAt = 1)
)

@Preview(name = "新建样本", showBackground = true, heightDp = 1200)
@Composable
private fun SampleEditNewPreview() {
    GrowthOSTheme {
        SampleEditContent(
            state = SampleEditUiState(
                domains = previewDomains,
                errorTypes = previewErrorTypes,
                form = SampleForm(domainId = 1, attribution = Attribution.CONTROLLABLE)
            ),
            onUpdateDomain = {}, onUpdateResult = {}, onUpdateDescription = {},
            onUpdateErrorType = {}, onUpdateAttribution = {}, onUpdateEmotion = {},
            onUpdateReview = {}, onOpenNewErrorType = {}, onDismissNewErrorType = {},
            onCreateErrorType = {}, onLongClickErrorType = {}, onSave = {}, onDelete = {}, onBack = {},
            onNavigateToDomains = {},
            pendingDelete = null, blockedCount = null,
            onConfirmDeleteErrorType = {}, onDismissDeleteErrorType = {}, onDismissBlocked = {}
        )
    }
}

@Preview(name = "编辑样本(预填)", showBackground = true, heightDp = 1200)
@Composable
private fun SampleEditEditingPreview() {
    GrowthOSTheme {
        SampleEditContent(
            state = SampleEditUiState(
                domains = previewDomains,
                errorTypes = previewErrorTypes,
                form = SampleForm(
                    domainId = 1,
                    result = "线上 bug,影响部分退款订单",
                    description = "退款金额为 0 的分支没处理",
                    errorTypeId = 1,
                    attribution = Attribution.CONTROLLABLE,
                    emotionIntensity = 4,
                    review = "退款金额为 0 的分支要单独测一遍"
                ),
                isEditing = true
            ),
            onUpdateDomain = {}, onUpdateResult = {}, onUpdateDescription = {},
            onUpdateErrorType = {}, onUpdateAttribution = {}, onUpdateEmotion = {},
            onUpdateReview = {}, onOpenNewErrorType = {}, onDismissNewErrorType = {},
            onCreateErrorType = {}, onLongClickErrorType = {}, onSave = {}, onDelete = {}, onBack = {},
            onNavigateToDomains = {},
            pendingDelete = null, blockedCount = null,
            onConfirmDeleteErrorType = {}, onDismissDeleteErrorType = {}, onDismissBlocked = {}
        )
    }
}

@Preview(name = "无领域引导", showBackground = true, heightDp = 400)
@Composable
private fun SampleEditNoDomainPreview() {
    GrowthOSTheme {
        SampleEditContent(
            state = SampleEditUiState(),
            onUpdateDomain = {}, onUpdateResult = {}, onUpdateDescription = {},
            onUpdateErrorType = {}, onUpdateAttribution = {}, onUpdateEmotion = {},
            onUpdateReview = {}, onOpenNewErrorType = {}, onDismissNewErrorType = {},
            onCreateErrorType = {}, onLongClickErrorType = {}, onSave = {}, onDelete = {}, onBack = {},
            onNavigateToDomains = {},
            pendingDelete = null, blockedCount = null,
            onConfirmDeleteErrorType = {}, onDismissDeleteErrorType = {}, onDismissBlocked = {}
        )
    }
}
