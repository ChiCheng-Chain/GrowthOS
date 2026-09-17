package com.growthos.app.ui.error_type

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.growthos.app.domain.model.Polarity
import com.growthos.app.ui.components.Eyebrow

/**
 * 新建 / 改名共用底部对话框(CRUD 补全,仿 DomainEditDialog)。
 *
 * - 新建态([ErrorTypeDialog.Create]):空名称 + 极性选择(默认负向,feature 2026-09-16 / 设计 D7)。
 * - 编辑态([ErrorTypeDialog.Edit]):预填名称,极性锁定不可改(防「同名词忽正忽负」污染统计口径)。
 * - 校验:名称 trim 后非空且 ≤ 20 字符。
 * - 重名:软提示行,不阻断(撞名由 Repository rename 走合并)。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ErrorTypeEditDialog(
    dialog: ErrorTypeDialog,
    hasDuplicate: (String) -> Boolean,
    onSave: (name: String, polarity: Polarity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEdit = dialog is ErrorTypeDialog.Edit
    val editing: ErrorType? = (dialog as? ErrorTypeDialog.Edit)?.errorType

    var name by remember(dialog) { mutableStateOf(editing?.name ?: "") }
    var polarity by remember(dialog) { mutableStateOf(editing?.polarity ?: Polarity.NEGATIVE) }

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
            Eyebrow(if (isEdit) "编辑关键因素" else "新建关键因素")
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

            // 极性选择:仅新建态可改;编辑态展示锁定值(设计 D7)
            Spacer(Modifier.height(16.dp))
            if (isEdit) {
                HintText(if (polarity == Polarity.POSITIVE) "极性:正向(不可修改)" else "极性:负向(不可修改)")
            } else {
                HintText("极性")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Polarity.entries.forEach { p ->
                        val selected = p == polarity
                        val bg = if (selected) MaterialTheme.colorScheme.onBackground
                        else MaterialTheme.colorScheme.surfaceVariant
                        val fg = if (selected) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                        Text(
                            text = if (p == Polarity.NEGATIVE) "负向(错误)" else "正向(做对的事)",
                            style = MaterialTheme.typography.labelLarge,
                            color = fg,
                            modifier = Modifier
                                .background(bg)
                                .clickable { polarity = p }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
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
                    onClick = { if (valid) onSave(trimmed, polarity) },
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
