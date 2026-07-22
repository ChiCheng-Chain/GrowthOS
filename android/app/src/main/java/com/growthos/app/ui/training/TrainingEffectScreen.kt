package com.growthos.app.ui.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.growthos.app.GrowthOSApp
import com.growthos.app.data.local.entity.Sample
import com.growthos.app.data.local.entity.Training
import com.growthos.app.data.local.relation.SampleWithErrorType
import com.growthos.app.data.local.relation.TrainingEffectStats
import com.growthos.app.domain.model.Attribution
import com.growthos.app.domain.model.TrainingStatus
import com.growthos.app.ui.components.Eyebrow
import com.growthos.app.ui.components.LedgerMetric
import com.growthos.app.ui.components.LedgerRule
import com.growthos.app.ui.components.NextActionBlock
import com.growthos.app.ui.components.PageHeader
import com.growthos.app.ui.theme.GrowthOSTheme
import com.growthos.app.ui.theme.MonoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 训练效果页(二期阶段 5 / 需求 F4 / R-011)。
 *
 * 展示训练前后该错误类型出现次数 + 训练后相关样本列表 + 训练项信息。
 * effectStats 一次性拉取(D2);observeSamplesAfter 为 Flow(样本列表实时)。
 */
@Composable
fun TrainingEffectScreen(
    trainingId: Long,
    onBack: () -> Unit
) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val vm: TrainingEffectViewModel = viewModel(
        factory = TrainingEffectViewModel.Factory(
            container.trainingRepository,
            container.sampleRepository,
            container.errorTypeRepository,
            container.domainRepository,
            trainingId
        )
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    TrainingEffectContent(state = state, onBack = onBack)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TrainingEffectContent(
    state: TrainingEffectUiState,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("训练效果", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            if (!state.loaded || state.training == null) {
                Spacer(Modifier.height(40.dp))
                Text(
                    "加载中…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            } else {
                val t = state.training!!
                PageHeader(
                    eyebrow = "训练效果",
                    title = state.errorTypeName ?: "训练项",
                    subtitle = "${state.domainName} · ${t.status.label} · 开始 ${formatDate(t.startedAt)}"
                )

                // 训练目标 + 验收标准
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Eyebrow("训练目标")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        t.goal,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!t.acceptanceCriteria.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "验收标准:${t.acceptanceCriteria}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))

                // 前后对比(两个大数字并排)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    LedgerMetric(
                        value = state.stats?.beforeCount?.toString() ?: "—",
                        label = "训练前",
                        modifier = Modifier.weight(1f)
                    )
                    LedgerMetric(
                        value = state.stats?.afterCount?.toString() ?: "—",
                        label = "训练后",
                        modifier = Modifier.weight(1f),
                        accent = true
                    )
                }
                LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))

                // 训练后相关样本列表
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Eyebrow("训练后样本(${state.afterSamples.size})")
                    Spacer(Modifier.height(4.dp))
                    if (state.afterSamples.isEmpty()) {
                        Text(
                            "训练后还没有记录该类错误,继续保持",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.afterSamples.forEach { row ->
                            AfterSampleRow(row = row)
                        }
                    }
                }
                LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))

                // 备注(若有)
                if (!t.note.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Eyebrow("备注")
                        Spacer(Modifier.height(6.dp))
                        NextActionBlock(text = t.note)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AfterSampleRow(row: SampleWithErrorType) {
    Surface(
        onClick = {},
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatTime(row.sample.recordedAt),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = MonoFamily
                )
                Text(
                    "${row.errorTypeName} · ${row.sample.attribution.label}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                row.sample.result,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
private fun formatTime(epochMillis: Long): String = timeFmt.format(Date(epochMillis))
private fun formatDate(epochMillis: Long): String = dateFmt.format(Date(epochMillis))

// ---------- Previews ----------

private val previewTraining = Training(
    id = 1, domainId = 1, errorTypeId = 1, goal = "涉及分支/边界先列清单再写代码",
    acceptanceCriteria = "连续 3 次不漏边界", startedAt = 1000000L, endedAt = null,
    status = TrainingStatus.IN_PROGRESS, note = "重点是退款金额为 0 的分支"
)
private val previewSample = SampleWithErrorType(
    sample = Sample(
        id = 1, domainId = 1, recordedAt = 2000000L,
        result = "退款分支又漏了", description = "金额为 0",
        errorTypeId = 1, attribution = Attribution.CONTROLLABLE,
        emotionIntensity = 3, review = "先列状态表"
    ),
    errorTypeName = "边界条件遗漏",
    domainName = "编程"
)

@Preview(name = "训练效果(有样本)", showBackground = true, heightDp = 1100)
@Composable
private fun TrainingEffectDataPreview() {
    GrowthOSTheme {
        TrainingEffectContent(
            state = TrainingEffectUiState(
                training = previewTraining,
                errorTypeName = "边界条件遗漏",
                domainName = "编程",
                stats = TrainingEffectStats(beforeCount = 6, afterCount = 2),
                afterSamples = listOf(previewSample),
                loaded = true
            ),
            onBack = {}
        )
    }
}

@Preview(name = "训练效果(训练后无样本)", showBackground = true, heightDp = 900)
@Composable
private fun TrainingEffectEmptyAfterPreview() {
    GrowthOSTheme {
        TrainingEffectContent(
            state = TrainingEffectUiState(
                training = previewTraining,
                errorTypeName = "边界条件遗漏",
                domainName = "编程",
                stats = TrainingEffectStats(beforeCount = 4, afterCount = 0),
                afterSamples = emptyList(),
                loaded = true
            ),
            onBack = {}
        )
    }
}
