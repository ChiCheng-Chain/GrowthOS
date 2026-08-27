package com.growthos.app.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.growthos.app.GrowthOSApp
import com.growthos.app.data.local.entity.Sample
import com.growthos.app.data.repository.SampleRepository
import com.growthos.app.ui.components.Eyebrow
import com.growthos.app.ui.components.LedgerRule
import com.growthos.app.ui.components.NextActionBlock
import com.growthos.app.ui.components.PageHeader
import com.growthos.app.ui.theme.GrowthOSTheme
import com.growthos.app.ui.theme.MonoFamily
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 今日记录页 — 产品定位 8.1:打开即记录,30 秒到 2 分钟内完成一次关键样本记录。
 *
 * 阶段 2:今日样本列表接 [SampleRepository.observeToday],替换 SampleStub 占位;
 * 快速记录入口与列表项点击导航 SampleEdit。
 */
@Composable
fun RecordScreen(
    onNavigateToEdit: (Long?) -> Unit
) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val vm: TodayListViewModel = viewModel(factory = TodayListViewModel.Factory(container.sampleRepository))
    val state by vm.uiState.collectAsStateWithLifecycle()

    RecordContent(
        todaySamples = state.samples,
        onQuickRecord = { onNavigateToEdit(null) },
        onOpenSample = { onNavigateToEdit(it.id) }
    )
}

@Composable
private fun RecordContent(
    todaySamples: List<Sample>,
    onQuickRecord: () -> Unit,
    onOpenSample: (Sample) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        PageHeader(
            eyebrow = "今日记录",
            title = "记下关键样本",
            subtitle = "一次复盘从记录开始。30 秒 ~ 2 分钟,只写最关键的。"
        )

        // 快速记录入口:横通栏,墨底白字,本页唯一的实色块。
        Surface(
            onClick = onQuickRecord,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.background
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "快速记录一次样本",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "领域 · 结果 · 错误 · 归因 · 复盘",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.background
                    )
                }
                Icon(Icons.Outlined.Add, contentDescription = "记录", modifier = Modifier.padding(start = 12.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        // 今日样本列表
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Eyebrow("今日样本")
            Text(
                "${todaySamples.size} 条",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = MonoFamily
            )
        }
        LedgerRule(modifier = Modifier.padding(top = 10.dp))

        if (todaySamples.isEmpty()) {
            TodayEmpty()
        } else {
            todaySamples.forEach { sample ->
                SampleRow(sample, onClick = { onOpenSample(sample) })
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun TodayEmpty() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            "今天还没记",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "点上面的“快速记录”,花一分钟记下今天最关键的一条。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    LedgerRule()
}

@Composable
private fun SampleRow(sample: Sample, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 时间(等宽)——今日列表不重复领域名(领域已在录入页选),
                // 但保留时间锚点;情绪强度若非空附在时间行。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatTime(sample.recordedAt),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = MonoFamily
                    )
                    sample.emotionIntensity?.let { emo ->
                        Text(
                            "情绪 $emo/5",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = MonoFamily
                        )
                    }
                }
                // 结果
                Text(
                    sample.result,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold
                )
                // 归因
                Text(
                    sample.attribution.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 复盘(琥珀强调)
                NextActionBlock(text = sample.review)
            }
        }
        LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private fun formatTime(epochMillis: Long): String = timeFormat.format(Date(epochMillis))

/**
 * 今日列表 ViewModel(阶段 2):只持有 [SampleRepository.observeToday]。
 * 按记录摩擦原则保持极简——今日列表是被动展示,无用户操作状态。
 */
internal class TodayListViewModel(repository: SampleRepository) : ViewModel() {

    data class UiState(val samples: List<Sample> = emptyList())

    val uiState: StateFlow<UiState> = repository.observeToday()
        .map { UiState(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    class Factory(private val repository: SampleRepository) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TodayListViewModel(repository) as T
    }
}

// ---------- Previews ----------

private val previewSamples = listOf(
    Sample(
        id = 1, domainId = 1, recordedAt = System.currentTimeMillis(),
        result = "线上 bug,影响部分退款订单",
        errorTypeId = 1, attribution = com.growthos.app.domain.model.Attribution.CONTROLLABLE,
        emotionIntensity = 4, review = "退款金额为 0 的分支要单独测一遍"
    ),
    Sample(
        id = 2, domainId = 2, recordedAt = System.currentTimeMillis() - 3600_000,
        result = "第 7 名 / 8 人",
        errorTypeId = 2, attribution = com.growthos.app.domain.model.Attribution.CONTROLLABLE,
        emotionIntensity = 3, review = "锁血局优先保前四,不追第一"
    )
)

@Preview(name = "今日记录", showBackground = true, heightDp = 900)
@Composable
private fun RecordContentPreview() {
    GrowthOSTheme {
        RecordContent(
            todaySamples = previewSamples,
            onQuickRecord = {},
            onOpenSample = {}
        )
    }
}

@Preview(name = "今日空状态", showBackground = true, heightDp = 600)
@Composable
private fun RecordEmptyPreview() {
    GrowthOSTheme {
        RecordContent(
            todaySamples = emptyList(),
            onQuickRecord = {},
            onOpenSample = {}
        )
    }
}
