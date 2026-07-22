package com.growthos.app.ui.record

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.ui.components.Eyebrow
import com.growthos.app.ui.theme.GrowthOSTheme

/**
 * 错误类型内联选择 + 新建(R-004 / 设计 D4)。
 *
 * - 已有错误类型全局共享(跨领域复用),按创建时间升序展示,单选。
 * - 末尾"+ 新建"chip → 底部对话框输入名 → 保存。重名走 getOrCreate 复用,立即选中。
 * - 选中态用墨色高亮。
 * - 阶段 7(R-014):chip 长按触发删除流程(查引用 → 拦截 / 确认)。
 *
 * [onCreateNew] 由 ViewModel 的 createErrorType 承接(getOrCreate + 设 form.errorTypeId)。
 * [onLongClickErrorType] 由 ViewModel 的 requestDeleteErrorType 承接(查引用 → 事件)。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
fun ErrorTypePickerInline(
    errorTypes: List<ErrorType>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onCreateNew: (String) -> Unit,
    isDialogOpen: Boolean,
    onOpenDialog: () -> Unit,
    onDismissDialog: () -> Unit,
    onLongClickErrorType: (ErrorType) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        errorTypes.forEach { et ->
            val isSelected = et.id == selectedId
            val bg = if (isSelected) MaterialTheme.colorScheme.onBackground
            else MaterialTheme.colorScheme.surfaceVariant
            val fg = if (isSelected) MaterialTheme.colorScheme.background
            else MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                text = et.name,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                modifier = Modifier
                    .background(bg)
                    .combinedClickable(
                        onClick = { onSelect(et.id) },
                        onLongClick = { onLongClickErrorType(et) }
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        NewErrorTypeChip(onClick = onOpenDialog)
    }

    if (isDialogOpen) {
        NewErrorTypeDialog(
            onConfirm = onCreateNew,
            onDismiss = onDismissDialog
        )
    }
}

@Composable
private fun NewErrorTypeChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "新建错误类型", modifier = Modifier.width(12.dp))
            Text("新建", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * 新建错误类型底部对话框。复刻 [com.growthos.app.ui.domain.DomainEditDialog] 范式:
 * 单字段名称 + 校验非空 + 取消/保存。重名由 ViewModel 的 getOrCreate 兜底复用,此处不查重。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NewErrorTypeDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val trimmed = name.trim()
    val valid = trimmed.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Eyebrow("新建错误类型")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true,
                isError = name.isNotEmpty() && trimmed.isEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            if (name.isNotEmpty() && trimmed.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "名称不能为空",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { if (valid) onConfirm(trimmed) },
                    enabled = valid
                ) {
                    Text(
                        "保存",
                        fontWeight = FontWeight.SemiBold,
                        color = if (valid) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ---------- Previews ----------

@Preview(name = "错误类型选择", showBackground = true, heightDp = 200)
@Composable
private fun ErrorTypePickerInlinePreview() {
    GrowthOSTheme {
        ErrorTypePickerInline(
            errorTypes = listOf(
                ErrorType(id = 1, name = "边界条件遗漏", createdAt = 0),
                ErrorType(id = 2, name = "压力下急躁", createdAt = 1),
                ErrorType(id = 3, name = "复查不足", createdAt = 2)
            ),
            selectedId = 1L,
            onSelect = {},
            onCreateNew = {},
            isDialogOpen = false,
            onOpenDialog = {},
            onDismissDialog = {},
            onLongClickErrorType = {}
        )
    }
}
