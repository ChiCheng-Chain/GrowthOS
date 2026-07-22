package com.growthos.app.ui.error_type

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.ui.components.Eyebrow
import com.growthos.app.ui.components.LedgerRule
import com.growthos.app.ui.components.NextActionBlock
import com.growthos.app.ui.theme.GrowthOSTheme
import com.growthos.app.ui.theme.MonoFamily

/**
 * 错误类型管理页(CRUD 补全)。
 *
 * 周复盘页顶部入口。集中管理全部错误类型:新建 / 改名(撞名走合并)/ 删除(引用检查)。
 * Sample/Training/Principle 编辑页的内联 chip 选择仍保留,本页是集中管理入口。
 */
@Composable
fun ErrorTypeListScreen(
    onBack: () -> Unit
) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val vm: ErrorTypeListViewModel = viewModel(
        factory = ErrorTypeListViewModel.Factory(container.errorTypeRepository)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf<ErrorType?>(null) }
    var blockedCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(vm) {
        vm.deleteEvents.collect { event ->
            when (event) {
                is ErrorTypeDeleteEvent.Blocked -> blockedCount = event.referenceCount
                is ErrorTypeDeleteEvent.ConfirmDelete -> pendingDelete = event.errorType
                ErrorTypeDeleteEvent.ErrorTypeDeleted -> pendingDelete = null
            }
        }
    }

    ErrorTypeListContent(
        state = state,
        onBack = onBack,
        onOpenCreate = vm::openCreate,
        onOpenEdit = vm::openEdit,
        onRename = vm::rename,
        onCreate = vm::create,
        onDismissDialog = vm::dismissDialog,
        hasDuplicate = vm::hasDuplicate,
        onRequestDelete = vm::requestDeleteErrorType,
        onConfirmDelete = vm::confirmDeleteErrorType,
        onDismissDelete = { pendingDelete = null },
        onDismissBlocked = { blockedCount = null },
        pendingDelete = pendingDelete,
        blockedCount = blockedCount
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ErrorTypeListContent(
    state: ErrorTypeListUiState,
    onBack: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenEdit: (ErrorType) -> Unit,
    onRename: (Long, String) -> Unit,
    onCreate: (String) -> Unit,
    onDismissDialog: () -> Unit,
    hasDuplicate: (String, Long?) -> Boolean,
    onRequestDelete: (ErrorType) -> Unit,
    onConfirmDelete: (ErrorType) -> Unit,
    onDismissDelete: () -> Unit,
    onDismissBlocked: () -> Unit,
    pendingDelete: ErrorType?,
    blockedCount: Int?
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("错误类型", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenCreate) {
                        Icon(Icons.Outlined.Add, contentDescription = "新建错误类型")
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
                .fillMaxWidth()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            if (state.isEmpty) {
                EmptyHint()
            } else {
                state.errorTypes.forEach { et ->
                    ErrorTypeRow(
                        errorType = et,
                        onClick = { onOpenEdit(et) },
                        onDelete = { onRequestDelete(et) }
                    )
                    LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    // 新建 / 改名对话框
    state.dialog?.let { dialog ->
        val excludeId = (dialog as? ErrorTypeDialog.Edit)?.errorType?.id
        ErrorTypeEditDialog(
            dialog = dialog,
            hasDuplicate = { name -> hasDuplicate(name, excludeId) },
            onSave = { name ->
                when (dialog) {
                    is ErrorTypeDialog.Create -> onCreate(name)
                    is ErrorTypeDialog.Edit -> onRename(dialog.errorType.id, name)
                }
            },
            onDismiss = onDismissDialog
        )
    }

    // 被引用 → 拦截提示
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

    // 未引用 → 确认删除
    pendingDelete?.let { et ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("删除错误类型") },
            text = { Text("删除「${et.name}」?未引用时可直接删除。") },
            confirmButton = {
                TextButton(onClick = { onConfirmDelete(et) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ErrorTypeRow(
    errorType: ErrorType,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                errorType.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onDelete) {
                Text("删除", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Eyebrow("还没有错误类型")
        Spacer(Modifier.height(10.dp))
        NextActionBlock(
            label = "开始",
            text = "错误类型跨领域复用,用来标记样本和训练项针对的失误。点右上角新建第一个,或录样本时内联创建。"
        )
    }
}

// ---------- Previews ----------

private val previewErrorTypes = listOf(
    ErrorType(id = 1, name = "边界条件遗漏", createdAt = 0),
    ErrorType(id = 2, name = "压力下急躁", createdAt = 1),
    ErrorType(id = 3, name = "复查不足", createdAt = 2)
)

@Preview(name = "错误类型列表(有数据)", showBackground = true, heightDp = 600)
@Composable
private fun ErrorTypeListDataPreview() {
    GrowthOSTheme {
        ErrorTypeListContent(
            state = ErrorTypeListUiState(errorTypes = previewErrorTypes),
            onBack = {}, onOpenCreate = {}, onOpenEdit = {}, onRename = { _, _ -> },
            onCreate = {}, onDismissDialog = {}, hasDuplicate = { _, _ -> false },
            onRequestDelete = {}, onConfirmDelete = {}, onDismissDelete = {},
            onDismissBlocked = {}, pendingDelete = null, blockedCount = null
        )
    }
}

@Preview(name = "错误类型列表(空)", showBackground = true, heightDp = 500)
@Composable
private fun ErrorTypeListEmptyPreview() {
    GrowthOSTheme {
        ErrorTypeListContent(
            state = ErrorTypeListUiState(),
            onBack = {}, onOpenCreate = {}, onOpenEdit = {}, onRename = { _, _ -> },
            onCreate = {}, onDismissDialog = {}, hasDuplicate = { _, _ -> false },
            onRequestDelete = {}, onConfirmDelete = {}, onDismissDelete = {},
            onDismissBlocked = {}, pendingDelete = null, blockedCount = null
        )
    }
}
