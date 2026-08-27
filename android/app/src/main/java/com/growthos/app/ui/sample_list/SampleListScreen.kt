package com.growthos.app.ui.sample_list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.growthos.app.GrowthOSApp
import com.growthos.app.data.local.entity.Sample
import com.growthos.app.data.local.relation.SampleWithErrorType
import com.growthos.app.domain.model.Attribution
import com.growthos.app.ui.components.LedgerRule
import com.growthos.app.ui.components.SampleFilterSheet
import com.growthos.app.ui.theme.GrowthOSTheme
import com.growthos.app.ui.theme.MonoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 样本全量列表页。领域页"查看全部"入口进入,展示某领域全量样本 + 筛选。
 * 紧凑行不展示"下次怎么做"复盘块(密度优先,点进去看详情)。对齐 TrainingListScreen 范式。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SampleListScreen(
    domainId: Long,
    onBack: () -> Unit,
    onOpenSample: (Long) -> Unit
) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val vm: SampleListViewModel = viewModel(
        factory = SampleListViewModel.Factory(container.sampleRepository, domainId)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    var filterSheetOpen by remember { mutableStateOf(false) }

    SampleListContent(
        state = state,
        onBack = onBack,
        onOpenSample = onOpenSample,
        onErrorTypeSelect = vm::filterByErrorType,
        onAttributionSelect = vm::filterByAttribution,
        onClearFilter = vm::clearFilter,
        onOpenFilter = { filterSheetOpen = true },
        filterSheetOpen = filterSheetOpen,
        onDismissFilterSheet = { filterSheetOpen = false }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SampleListContent(
    state: SampleListUiState,
    onBack: () -> Unit,
    onOpenSample: (Long) -> Unit,
    onErrorTypeSelect: (Long?) -> Unit,
    onAttributionSelect: (Attribution?) -> Unit,
    onClearFilter: () -> Unit = {},
    onOpenFilter: () -> Unit = {},
    filterSheetOpen: Boolean = false,
    onDismissFilterSheet: () -> Unit = {}
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("全部样本", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // 筛选入口行(feature 2026-08-27):取代常驻 FilterBar,收进底部弹层。
            val activeCount = listOfNotNull(state.filter.errorTypeId, state.filter.attribution).size
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${state.filteredSamples.size} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = MonoFamily
                )
                if (activeCount == 0) {
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
                        "筛选 · $activeCount 项",
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

            if (state.filteredSamples.isEmpty()) {
                Text(
                    text = if (state.filter.errorTypeId == null && state.filter.attribution == null)
                        "还没有样本"
                    else "没有符合条件的样本",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
            } else {
                state.filteredSamples.forEach { row ->
                    SampleListRow(row = row, onClick = { onOpenSample(row.sample.id) })
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    SampleFilterSheet(
        visible = filterSheetOpen,
        availableErrorTypes = state.availableErrorTypes,
        filter = state.filter.errorTypeId to state.filter.attribution,
        errorTypeName = { id ->
            state.availableErrorTypes.firstOrNull { it.errorTypeId == id }?.errorTypeName
        },
        onErrorTypeSelect = onErrorTypeSelect,
        onAttributionSelect = onAttributionSelect,
        onClearAll = onClearFilter,
        onDismiss = onDismissFilterSheet
    )
}

/** 紧凑样本行:时间 + 错误类型·归因 一行,结果一行。不展示复盘块,密度优先。 */
@Composable
private fun SampleListRow(row: SampleWithErrorType, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatTime(row.sample.recordedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = MonoFamily
                )
                Text(
                    "${row.errorTypeName} · ${row.sample.attribution.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                row.sample.result,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
    LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))
}

private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
private fun formatTime(epochMillis: Long): String = timeFmt.format(Date(epochMillis))

// ---------- Previews ----------

@Preview(name = "样本全量列表(有数据)", showBackground = true, heightDp = 900)
@Composable
private fun SampleListContentDataPreview() {
    GrowthOSTheme {
        SampleListContent(
            state = SampleListUiState(
                filteredSamples = listOf(
                    SampleWithErrorType(
                        sample = Sample(
                            id = 1, domainId = 1, recordedAt = System.currentTimeMillis(),
                            result = "线上 bug",
                            errorTypeId = 1, attribution = Attribution.CONTROLLABLE,
                            emotionIntensity = 4, review = "先列状态表"
                        ),
                        errorTypeName = "边界条件遗漏", domainName = "编程"
                    ),
                    SampleWithErrorType(
                        sample = Sample(
                            id = 2, domainId = 1, recordedAt = System.currentTimeMillis() - 3600_000,
                            result = "双打输 18:21",
                            errorTypeId = 2, attribution = Attribution.CONTROLLABLE,
                            emotionIntensity = 4, review = "接杀先回位"
                        ),
                        errorTypeName = "压力下急躁", domainName = "编程"
                    )
                )
            ),
            onBack = {}, onOpenSample = {}, onErrorTypeSelect = {}, onAttributionSelect = {},
            onClearFilter = {}, onOpenFilter = {}
        )
    }
}

@Preview(name = "样本全量列表(空)", showBackground = true, heightDp = 600)
@Composable
private fun SampleListContentEmptyPreview() {
    GrowthOSTheme {
        SampleListContent(
            state = SampleListUiState(),
            onBack = {}, onOpenSample = {}, onErrorTypeSelect = {}, onAttributionSelect = {},
            onClearFilter = {}, onOpenFilter = {}
        )
    }
}
