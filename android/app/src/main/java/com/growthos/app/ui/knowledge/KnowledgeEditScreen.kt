package com.growthos.app.ui.knowledge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.growthos.app.domain.model.KnowledgeType
import com.growthos.app.ui.components.LedgerRule
import com.growthos.app.ui.theme.GrowthOSTheme

/**
 * 知识编辑页。对齐 [com.growthos.app.ui.principle.PrincipleEditScreen] 范式。
 * 新建/编辑共用。内容必填,类型(经验/待办)必选,领域可选。
 */
@Composable
fun KnowledgeEditScreen(
    knowledgeId: Long?,
    prefillDomainId: Long? = null,
    onBack: () -> Unit
) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val vm: KnowledgeEditViewModel = viewModel(
        factory = KnowledgeEditViewModel.Factory(
            container.knowledgeRepository,
            container.domainRepository,
            knowledgeId,
            prefillDomainId
        )
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                KnowledgeEditEvent.Saved, KnowledgeEditEvent.Deleted -> onBack()
            }
        }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    KnowledgeEditContent(
        state = state,
        onUpdateContent = vm::updateContent,
        onUpdateType = vm::updateType,
        onUpdateDomain = vm::updateDomain,
        onSave = vm::save,
        onDelete = { showDeleteConfirm = true },
        onBack = onBack
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除知识") },
            text = { Text("删除这条知识?不可恢复。") },
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
private fun KnowledgeEditContent(
    state: KnowledgeEditUiState,
    onUpdateContent: (String) -> Unit,
    onUpdateType: (KnowledgeType) -> Unit,
    onUpdateDomain: (Long?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val form = state.form

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isEditing) "编辑知识" else "新建知识",
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
            // 无可见领域时仍可建(领域可选)
            // 1. 类型(经验/待办)
            FieldLabel("类型")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KnowledgeType.entries.forEach { type ->
                    TypeChip(
                        text = type.label,
                        selected = form.type == type,
                        onClick = { onUpdateType(type) }
                    )
                }
            }
            LedgerRule(modifier = Modifier.padding(top = 12.dp))

            // 2. 内容
            FieldLabel("内容")
            OutlinedTextField(
                value = form.content,
                onValueChange = onUpdateContent,
                placeholder = { Text("写下这条知识") },
                singleLine = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            )
            LedgerRule(modifier = Modifier.padding(top = 12.dp))

            // 3. 领域(可选)
            if (state.domains.isNotEmpty()) {
                FieldLabel("领域", required = false)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 「无」chip 清空关联
                    DomainPickChip(
                        name = "无",
                        selected = form.domainId == null,
                        onClick = { onUpdateDomain(null) }
                    )
                    state.domains.forEach { domain ->
                        DomainPickChip(
                            name = domain.name,
                            selected = domain.id == form.domainId,
                            onClick = { onUpdateDomain(domain.id) }
                        )
                    }
                }
                LedgerRule(modifier = Modifier.padding(top = 12.dp))
            }

            // 保存
            Spacer(Modifier.height(20.dp))
            SaveButton(enabled = form.isValid, onSave = onSave)

            // 删除(仅编辑态)
            if (state.isEditing) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "删除这条知识",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String, required: Boolean = true) {
    Text(
        text = if (required) "$text *" else text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}

@Composable
private fun TypeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.onBackground
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.background
    else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        modifier = Modifier
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
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

@Preview(name = "新建知识", showBackground = true, heightDp = 800)
@Composable
private fun KnowledgeEditNewPreview() {
    GrowthOSTheme {
        KnowledgeEditContent(
            state = KnowledgeEditUiState(
                domains = previewDomains,
                form = KnowledgeForm(type = KnowledgeType.EXPERIENCE)
            ),
            onUpdateContent = {}, onUpdateType = {}, onUpdateDomain = {},
            onSave = {}, onDelete = {}, onBack = {}
        )
    }
}

@Preview(name = "编辑知识(预填待办)", showBackground = true, heightDp = 800)
@Composable
private fun KnowledgeEditTodoPreview() {
    GrowthOSTheme {
        KnowledgeEditContent(
            state = KnowledgeEditUiState(
                domains = previewDomains,
                form = KnowledgeForm(
                    content = "下周试试新的发球站位",
                    type = KnowledgeType.TODO,
                    domainId = 2
                ),
                isEditing = true
            ),
            onUpdateContent = {}, onUpdateType = {}, onUpdateDomain = {},
            onSave = {}, onDelete = {}, onBack = {}
        )
    }
}
