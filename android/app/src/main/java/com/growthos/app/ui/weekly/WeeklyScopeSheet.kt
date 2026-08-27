package com.growthos.app.ui.weekly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.ui.components.FilterChipItem

/**
 * 周复盘口径弹层(feature 2026-08-27 信息分层改造,取代常驻两排 SelectorRow)。
 *
 * 时间(7/14/30 天)+ 领域(全部/单领域)两节,选中即时生效——
 * 直接回调既有 selectDays/selectDomain,弹层保持打开。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun WeeklyScopeSheet(
    visible: Boolean,
    days: Int,
    domainFilter: DomainFilter,
    availableDomains: List<Domain>,
    onSelectDays: (Int) -> Unit,
    onSelectDomain: (DomainFilter) -> Unit,
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
                "复盘口径",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Text(
                "时间范围",
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
                listOf(7, 14, 30).forEach { d ->
                    FilterChipItem(
                        label = "$d 天",
                        selected = days == d,
                        onClick = { onSelectDays(d) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "领域",
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
                    selected = domainFilter is DomainFilter.All,
                    onClick = { onSelectDomain(DomainFilter.All) }
                )
                availableDomains.forEach { domain ->
                    FilterChipItem(
                        label = domain.name,
                        selected = (domainFilter as? DomainFilter.Single)?.domainId == domain.id,
                        onClick = { onSelectDomain(DomainFilter.Single(domain.id)) }
                    )
                }
            }
        }
    }
}
