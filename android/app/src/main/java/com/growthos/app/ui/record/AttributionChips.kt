package com.growthos.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.growthos.app.domain.model.Attribution
import com.growthos.app.ui.components.Eyebrow

/**
 * 归因四选一 chips(R-005 / 设计 D4)。
 *
 * - 固定四值枚举,单选,必填。
 * - "可控错误"用 [colorScheme.primary](琥珀)标记选中态,其余用墨色,呼应
 *   [com.growthos.app.ui.components.NextActionBlock]——视觉上把"可训练的"和"面向未来行动"统一在一个色。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AttributionChips(
    selected: Attribution?,
    onSelect: (Attribution) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Attribution.entries.forEach { attr ->
            val isSelected = attr == selected
            val bg = when {
                isSelected && attr == Attribution.CONTROLLABLE -> MaterialTheme.colorScheme.primary
                isSelected -> MaterialTheme.colorScheme.onBackground
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val fg = if (isSelected) MaterialTheme.colorScheme.background
            else MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                text = attr.label,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                modifier = Modifier
                    .background(bg)
                    .clickable { onSelect(attr) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

/** 字段区块标签:章节眉标 + 可选提示。复用 Ledger 的 Eyebrow 视觉。 */
@Composable
fun FieldLabel(
    text: String,
    required: Boolean = true,
    modifier: Modifier = Modifier
) {
    Eyebrow(
        text = if (required) "$text *" else text,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}
