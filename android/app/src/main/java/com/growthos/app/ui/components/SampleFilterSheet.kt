package com.growthos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.growthos.app.data.local.relation.ErrorTypeCount
import com.growthos.app.domain.model.Attribution

/**
 * 样本筛选底部弹层(feature 2026-08-27 信息分层改造,取代常驻 SampleFilterBar)。
 *
 * 领域页与全量列表页共用:错误类型(出现过的+全部)一节、归因(四值+全部)一节,
 * 两维可叠加。选中**即时生效**——直接回调既有 filterBy*,弹层保持打开,
 * 半开状态下背后列表即时反馈(设计决策:零新状态,复用 ViewModel 既有逻辑)。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun SampleFilterSheet(
    visible: Boolean,
    availableErrorTypes: List<ErrorTypeCount>,
    filter: Pair<Long?, Attribution?>,
    errorTypeName: (Long) -> String?,
    onErrorTypeSelect: (Long?) -> Unit,
    onAttributionSelect: (Attribution?) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            SheetHeader(
                title = "筛选样本",
                actionText = "清除全部",
                showAction = filter.first != null || filter.second != null,
                onAction = onClearAll
            )

            SheetSectionLabel("按错误类型")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipItem(
                    label = "全部",
                    selected = filter.first == null,
                    onClick = { onErrorTypeSelect(null) }
                )
                availableErrorTypes.forEach { et ->
                    FilterChipItem(
                        label = "${et.errorTypeName} ${et.count}",
                        selected = filter.first == et.errorTypeId,
                        onClick = { onErrorTypeSelect(et.errorTypeId) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            SheetSectionLabel("按归因")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipItem(
                    label = "全部",
                    selected = filter.second == null,
                    onClick = { onAttributionSelect(null) }
                )
                Attribution.entries.forEach { attr ->
                    FilterChipItem(
                        label = attr.label,
                        selected = filter.second == attr,
                        onClick = { onAttributionSelect(attr) }
                    )
                }
            }
        }
    }
}

/** 弹层头:标题 + 右侧"清除全部"(仅有条件筛选时出现)。 */
@Composable
private fun SheetHeader(
    title: String,
    actionText: String,
    showAction: Boolean,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (showAction) {
            Text(
                actionText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onAction)
            )
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 筛选 chip:样式沿自原 SampleFilterBar.FilterChipItem(选中墨底纸字)。 */
@Composable
fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.onBackground
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.background
    else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        modifier = Modifier
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}
