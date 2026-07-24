package com.growthos.app.ui.principle

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
 * 原则编辑页(二期阶段 6 / 需求 F1)。
 *
 * 新建/编辑共用一页(D2)。壳层取 [PrincipleEditViewModel],收集 [PrincipleEditEvent]
 * 触发返回。内容层 [PrincipleEditContent] 纯展示,便于 @Preview。
 */
@Composable
fun PrincipleEditScreen(
    principleId: Long?,
    prefillTrainingId: Long? = null,
    prefillSampleId: Long? = null,
    prefillDomainId: Long? = null,
    onBack: () -> Unit
) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val vm: PrincipleEditViewModel = viewModel(
        factory = PrincipleEditViewModel.Factory(
            container.principleRepository,
            container.domainRepository,
            container.errorTypeRepository,
            principleId,
            prefillTrainingId,
            prefillSampleId,
            prefillDomainId
        )
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                PrincipleEditEvent.Saved, PrincipleEditEvent.Deleted -> onBack()
            }
        }
    }

    PrincipleEditContent(
        state = state,
        onUpdateContent = vm::updateContent,
        onUpdateDomain = vm::updateDomain,
        onUpdateErrorType = vm::updateErrorType,
        onSave = vm::save,
        onDelete = vm::delete,
        onBack = onBack
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
private fun PrincipleEditContent(
    state: PrincipleEditUiState,
    onUpdateContent: (String) -> Unit,
    onUpdateDomain: (Long?) -> Unit,
    onUpdateErrorType: (Long?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val form = state.form

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isEditing) "编辑原则" else "新建原则",
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
            // 1. 原则内容(必填,多行)
            FieldLabel("原则内容")
            OutlinedTextField(
                value = form.content,
                onValueChange = onUpdateContent,
                placeholder = { Text("把可迁移的认知写下来") },
                singleLine = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            )
            LedgerRule(modifier = Modifier.padding(top = 12.dp))

            // 2. 领域(可选,chips)
            FieldLabel("领域", required = false)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "无"选项清除关联
                PickChip(
                    name = "无",
                    selected = form.domainId == null,
                    onClick = { onUpdateDomain(null) }
                )
                state.domains.forEach { domain ->
                    PickChip(
                        name = domain.name,
                        selected = domain.id == form.domainId,
                        onClick = { onUpdateDomain(domain.id) }
                    )
                }
            }
            LedgerRule(modifier = Modifier.padding(top = 12.dp))

            // 3. 错误类型(可选,chips)
            FieldLabel("错误类型", required = false)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PickChip(
                    name = "无",
                    selected = form.errorTypeId == null,
                    onClick = { onUpdateErrorType(null) }
                )
                state.errorTypes.forEach { et ->
                    PickChip(
                        name = et.name,
                        selected = et.id == form.errorTypeId,
                        onClick = { onUpdateErrorType(et.id) }
                    )
                }
            }
            LedgerRule(modifier = Modifier.padding(top = 12.dp))

            // 保存
            Spacer(Modifier.height(20.dp))
            SaveButton(enabled = state.canSave, onSave = onSave)

            // 删除(仅编辑态)
            if (state.isEditing) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "删除这条原则",
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
            title = { Text("删除原则") },
            text = { Text("删除这条原则?不可恢复。") },
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
    Domain(id = 2, name = "羽毛球", createdAt = 1)
)
private val previewErrorTypes = listOf(
    ErrorType(id = 1, name = "边界条件遗漏", createdAt = 0),
    ErrorType(id = 2, name = "压力下急躁", createdAt = 1)
)

@Preview(name = "新建原则", showBackground = true, heightDp = 900)
@Composable
private fun PrincipleEditNewPreview() {
    GrowthOSTheme {
        PrincipleEditContent(
            state = PrincipleEditUiState(
                domains = previewDomains,
                errorTypes = previewErrorTypes
            ),
            onUpdateContent = {}, onUpdateDomain = {}, onUpdateErrorType = {},
            onSave = {}, onDelete = {}, onBack = {}
        )
    }
}

@Preview(name = "编辑原则(预填)", showBackground = true, heightDp = 900)
@Composable
private fun PrincipleEditEditingPreview() {
    GrowthOSTheme {
        PrincipleEditContent(
            state = PrincipleEditUiState(
                domains = previewDomains,
                errorTypes = previewErrorTypes,
                form = PrincipleForm(
                    content = "涉及分支/边界先列清单再写代码",
                    domainId = 1,
                    errorTypeId = 1
                ),
                isEditing = true
            ),
            onUpdateContent = {}, onUpdateDomain = {}, onUpdateErrorType = {},
            onSave = {}, onDelete = {}, onBack = {}
        )
    }
}
