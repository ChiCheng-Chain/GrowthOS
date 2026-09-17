package com.growthos.app.ui.practice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.growthos.app.data.local.entity.PracticeSession
import com.growthos.app.ui.theme.MonoFamily
import com.growthos.app.util.DurationFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 结束确认弹层(设计 D8 / UI-Q3):时长大字 + 起止 + 备注选填。
 * 「修改时间」次入口 → 编辑页(起点+时长同模型)。
 */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun FinishSheet(
    session: PracticeSession,
    onConfirm: (note: String?) -> Unit,
    onDismiss: () -> Unit,
    onEditTime: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        var note by remember { mutableStateOf(session.note ?: "") }
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                "这次练了",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                DurationFormat.formatMillis(System.currentTimeMillis() - session.startedAt),
                style = MaterialTheme.typography.displaySmall,
                fontFamily = MonoFamily,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatStart(session.startedAt)} – ${formatStart(System.currentTimeMillis())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注(选填)") },
                singleLine = true
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = { onConfirm(note) }, modifier = Modifier.fillMaxWidth()) {
                Text("保存")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                TextButton(onClick = onEditTime) {
                    Text("修改时间", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun formatStart(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
