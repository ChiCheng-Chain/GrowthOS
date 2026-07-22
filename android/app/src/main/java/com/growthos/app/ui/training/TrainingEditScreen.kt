package com.growthos.app.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
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
import com.growthos.app.ui.components.LedgerRule
import com.growthos.app.ui.theme.GrowthOSTheme

/**
 * 训练项编辑页(二期阶段 5 / 需求 F1)。
 *
 * 只做新建(D4)。预填 errorType 从周复盘 F5/F2 入口带入。壳层取 [TrainingEditViewModel],
 * 收集 [TrainingEditEvent.Saved] 触发返回。内容层 [TrainingEditContent] 纯展示,便于 @Preview。
 */
@Composable
fun TrainingEditScreen(
    prefillErrorTypeId: Long?,
    onBack: () -> Unit
) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val vm: TrainingEditViewModel = viewModel(
        factory = TrainingEditViewModel.Factory(
            container.trainingRepository,
            container.errorTypeRepository,
            container.domainRepository,
            container.selectedDomainStore,
            prefillErrorTypeId
        )
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                TrainingEditEvent.Saved -> onBack()
            }
        }
    }

    TrainingEditContent(
        state = state,
        onUpdateDomain = vm::updateDomain,
        onUpdateErrorType = vm::updateErrorType,
        onUpdateGoal = vm::updateGoal,
        onUpdateAcceptanceCriteria = vm::updateAcceptanceCriteria,
        onUpdateNote = vm::updateNote,
        onCreateErrorType = vm::createErrorType,
        onSave = vm::save,
        onBack = onBack
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
private fun TrainingEditContent(
    state: TrainingEditUiState,
    onUpdateDomain: (Long) -> Unit,
    onUpdateErrorType: (Long) -> Unit,
    onUpdateGoal: (String) -> Unit,
    onUpdateAcceptanceCriteria: (String) -> Unit,
    onUpdateNote: (String) -> Unit,
    onCreateErrorType: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    var showNewErrorTypeDialog by remember { mutableStateOf(false) }
    val form = state.form

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("新建训练项", style = MaterialTheme.typography.titleLarge) },
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
            // 1. 领域 chips(默认选中当前 / 预填)
            FieldLabel("领域")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.domains.forEach { domain ->
                    PickChip(
                        name = domain.name,
                        selected = domain.id == form.domainId,
                        onClick = { onUpdateDomain(domain.id) }
                    )
                }
            }
            LedgerRule(modifier = Modifier.padding(top = 12.dp))

            // 2. 错误类型(预填可改 + 新建)
            FieldLabel("错误类型")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.errorTypes.forEach { et ->
                    PickChip(
                        name = et.name,
                        selected = et.id == form.errorTypeId,
                        onClick = { onUpdateErrorType(et.id) }
                    )
                }
                NewErrorTypeChip(onClick = { showNewErrorTypeDialog = true })
            }
            LedgerRule(modifier = Modifier.padding(top = 12.dp))

            // 3. 训练目标(必填)
            FieldLabel("训练目标")
            FormTextField(
                value = form.goal,
                onValueChange = onUpdateGoal,
                placeholder = "一句话:想练成什么",
                singleLine = false
            )
            LedgerRule(modifier = Modifier.padding(top = 12.dp))

            // 4. 验收标准(可选)
            FieldLabel("验收标准", required = false)
            FormTextField(
                value = form.acceptanceCriteria,
                onValueChange = onUpdateAcceptanceCriteria,
                placeholder = "怎么算练成(可选)",
                singleLine = false
            )
            LedgerRule(modifier = Modifier.padding(top = 12.dp))

            // 5. 备注(可选)
            FieldLabel("备注", required = false)
            FormTextField(
                value = form.note,
                onValueChange = onUpdateNote,
                placeholder = "补充说明(可选)",
                singleLine = false
            )
            LedgerRule(modifier = Modifier.padding(top = 12.dp))

            // 保存
            Spacer(Modifier.height(20.dp))
            SaveButton(enabled = state.canSave, onSave = onSave)
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showNewErrorTypeDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewErrorTypeDialog = false },
            title = { Text("新建错误类型") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("错误类型名") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        onCreateErrorType(newName)
                        showNewErrorTypeDialog = false
                    }
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showNewErrorTypeDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun FieldLabel(text: String, required: Boolean = true) {
    Text(
        text = if (required) text else "$text(可选)",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun PickChip(name: String, selected: Boolean, onClick: () -> Unit) {
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
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun NewErrorTypeChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "新建错误类型")
            Text("新建", style = MaterialTheme.typography.labelLarge)
        }
    }
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

// ---------- Previews ----------

private val previewDomains = listOf(
    Domain(id = 1, name = "编程", createdAt = 0),
    Domain(id = 2, name = "酒馆战旗", createdAt = 1)
)
private val previewErrorTypes = listOf(
    ErrorType(id = 1, name = "边界条件遗漏", createdAt = 0),
    ErrorType(id = 2, name = "压力下急躁", createdAt = 1)
)

@Preview(name = "新建训练项(预填错误类型)", showBackground = true, heightDp = 1000)
@Composable
private fun TrainingEditPrefillPreview() {
    GrowthOSTheme {
        TrainingEditContent(
            state = TrainingEditUiState(
                domains = previewDomains,
                errorTypes = previewErrorTypes,
                form = TrainingForm(domainId = 1, errorTypeId = 1)
            ),
            onUpdateDomain = {}, onUpdateErrorType = {}, onUpdateGoal = {},
            onUpdateAcceptanceCriteria = {}, onUpdateNote = {}, onCreateErrorType = {},
            onSave = {}, onBack = {}
        )
    }
}

@Preview(name = "新建训练项(空)", showBackground = true, heightDp = 1000)
@Composable
private fun TrainingEditEmptyPreview() {
    GrowthOSTheme {
        TrainingEditContent(
            state = TrainingEditUiState(
                domains = previewDomains,
                errorTypes = previewErrorTypes
            ),
            onUpdateDomain = {}, onUpdateErrorType = {}, onUpdateGoal = {},
            onUpdateAcceptanceCriteria = {}, onUpdateNote = {}, onCreateErrorType = {},
            onSave = {}, onBack = {}
        )
    }
}
