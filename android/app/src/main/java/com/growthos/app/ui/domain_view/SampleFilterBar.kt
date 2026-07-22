package com.growthos.app.ui.domain_view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.growthos.app.data.local.relation.ErrorTypeCount
import com.growthos.app.domain.model.Attribution
import com.growthos.app.ui.components.Eyebrow

/**
 * F5 样本筛选条(设计 D2 / R-007 should)。
 *
 * 两排单选 chips:
 * - 错误类型:来自该领域出现过的错误类型([availableErrorTypes])+ "全部"。
 * - 归因:[Attribution] 四值 + "全部"。
 * 两者可叠加;点"全部"清该维筛选。选中态高亮。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SampleFilterBar(
    availableErrorTypes: List<ErrorTypeCount>,
    filter: SampleFilter,
    onErrorTypeSelect: (Long?) -> Unit,
    onAttributionSelect: (Attribution?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 错误类型筛选
        if (availableErrorTypes.isNotEmpty()) {
            Eyebrow("按错误类型", modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipItem(
                    label = "全部",
                    selected = filter.errorTypeId == null,
                    onClick = { onErrorTypeSelect(null) }
                )
                availableErrorTypes.forEach { et ->
                    FilterChipItem(
                        label = "${et.errorTypeName} ${et.count}",
                        selected = filter.errorTypeId == et.errorTypeId,
                        onClick = { onErrorTypeSelect(et.errorTypeId) }
                    )
                }
            }
        }

        // 归因筛选
        Eyebrow("按归因", modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChipItem(
                label = "全部",
                selected = filter.attribution == null,
                onClick = { onAttributionSelect(null) }
            )
            Attribution.entries.forEach { attr ->
                FilterChipItem(
                    label = attr.label,
                    selected = filter.attribution == attr,
                    onClick = { onAttributionSelect(attr) }
                )
            }
        }
    }
}

@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
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
