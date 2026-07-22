package com.growthos.app.ui.error_type

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.ui.components.Eyebrow

/**
 * 新建 / 改名共用底部对话框(CRUD 补全,仿 DomainEditDialog)。
 *
 * - 新建态([ErrorTypeDialog.Create]):空名称。
 * - 编辑态([ErrorTypeDialog.Edit]):预填名称。
 * - 校验:名称 trim 后非空且 ≤ 20 字符。
 * - 重名:软提示行,不阻断(撞名由 Repository rename 走合并)。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ErrorTypeEditDialog(
    dialog: ErrorTypeDialog,
    hasDuplicate: (String) -> Boolean,
    onSave: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEdit = dialog is ErrorTypeDialog.Edit
    val editing: ErrorType? = (dialog as? ErrorTypeDialog.Edit)?.errorType

    var name by remember(dialog) { mutableStateOf(editing?.name ?: "") }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(dialog) { focusRequester.requestFocus() }

    val trimmed = name.trim()
    val valid = trimmed.isNotEmpty() && trimmed.length <= 20
    val duplicate = valid && hasDuplicate(trimmed)

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
            Eyebrow(if (isEdit) "编辑错误类型" else "新建错误类型")
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
            Spacer(Modifier.height(6.dp))
            when {
                name.isNotEmpty() && trimmed.isEmpty() ->
                    HintText("名称不能为空")
                trimmed.length > 20 ->
                    HintText("名称不超过 20 字符")
                duplicate ->
                    HintText("已有同名,改名后将合并到已有项")
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
                    onClick = { if (valid) onSave(trimmed) },
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

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
