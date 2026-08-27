package com.growthos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.ui.theme.MonoFamily

/**
 * 领域筛选底部弹层(feature 2026-08-27 导航闭环与列表页领域筛选)。
 *
 * 训练/原则/知识三个列表页共用:单选维度(全部 + 各可见领域),
 * 选中即时生效——直接回调既有 filterByDomain,弹层保持打开。
 * 领域选项来自 observeVisible(不含已隐藏,Q-1 决策)。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun DomainFilterSheet(
    visible: Boolean,
    title: String,
    domainFilter: Long?,
    availableDomains: List<Domain>,
    onDomainSelect: (Long?) -> Unit,
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
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                "按领域",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipItem(
                    label = "全部",
                    selected = domainFilter == null,
                    onClick = { onDomainSelect(null) }
                )
                availableDomains.forEach { domain ->
                    FilterChipItem(
                        label = domain.name,
                        selected = domainFilter == domain.id,
                        onClick = { onDomainSelect(domain.id) }
                    )
                }
            }
        }
    }
}

/**
 * 列表页筛选入口行:左侧计数,右侧筛选按钮(未激活低调/激活实底胶囊,与样本卡同语言)。
 */
@Composable
fun FilterEntryRow(
    countText: String,
    activeLabel: String?,
    onOpenFilter: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            countText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = MonoFamily
        )
        if (activeLabel == null) {
            Text(
                "筛选",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onOpenFilter)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        } else {
            Text(
                "筛选 · $activeLabel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.onBackground)
                    .clickable(onClick = onOpenFilter)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
