package com.growthos.app.ui.weekly

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.relation.ControllableRatio
import com.growthos.app.data.local.relation.ErrorTypeCount
import com.growthos.app.data.local.relation.SampleWithErrorType
import com.growthos.app.domain.model.Attribution
import com.growthos.app.ui.components.LedgerMetric
import com.growthos.app.ui.components.LedgerRule
import com.growthos.app.ui.components.NextActionBlock
import com.growthos.app.ui.components.PageHeader
import com.growthos.app.ui.theme.GrowthOSTheme
import com.growthos.app.ui.theme.MonoFamily

/**
 * 周复盘页 — 产品定位 8.3 / R-009:
 *   本周记录数、高频错误前三、可控错误占比、情绪强度最高、建议关注的一个高频可控错误。
 *
 * 阶段 4:五项接 [WeeklyViewModel] 真聚合数据(替换 SampleStub 占位),
 * 加 F6 时间选择器(7/14/30 天)与 F7 跨/单领域切换(设计 §UI 组件)。
 * 阶段 5:F5 建议关注 / F2 高频错误行接建训练项入口,顶部加训练项列表入口(D1/D5)。
 * 阶段 6:顶部加原则库入口(D1)。
 */
@Composable
fun WeeklyScreen(
    onNavigateToCreateTraining: (Long) -> Unit = {},
    onNavigateToTrainingList: () -> Unit = {},
    onNavigateToPrincipleList: () -> Unit = {},
    onNavigateToErrorTypes: () -> Unit = {},
    onNavigateToKnowledge: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val vm: WeeklyViewModel = viewModel(
        factory = WeeklyViewModel.Factory(container.sampleRepository, container.domainRepository)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    WeeklyContent(
        state = state,
        onSelectDays = vm::selectDays,
        onSelectDomain = vm::selectDomain,
        onNavigateToCreateTraining = onNavigateToCreateTraining,
        onNavigateToTrainingList = onNavigateToTrainingList,
        onNavigateToPrincipleList = onNavigateToPrincipleList,
        onNavigateToErrorTypes = onNavigateToErrorTypes,
        onNavigateToKnowledge = onNavigateToKnowledge,
        onNavigateToSettings = onNavigateToSettings
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun WeeklyContent(
    state: WeeklyUiState,
    onSelectDays: (Int) -> Unit,
    onSelectDomain: (DomainFilter) -> Unit,
    onNavigateToCreateTraining: (Long) -> Unit = {},
    onNavigateToTrainingList: () -> Unit = {},
    onNavigateToPrincipleList: () -> Unit = {},
    onNavigateToErrorTypes: () -> Unit = {},
    onNavigateToKnowledge: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        PageHeader(
            eyebrow = "周复盘",
            title = "本周回顾",
            subtitle = "最近 ${state.days} 天 · ${state.sampleCount} 条样本"
        )

        // 阶段 5/6/7 + CRUD 补全:训练项 + 原则库 + 错误类型 + 设置入口。
        // 描边圆角小 tag,导航入口角色(与筛选 chips 区分)。FlowRow 允许窄屏换行。
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SubEntry("训练项", onNavigateToTrainingList)
            SubEntry("原则库", onNavigateToPrincipleList)
            SubEntry("错误类型", onNavigateToErrorTypes)
            SubEntry("知识库", onNavigateToKnowledge)
            SubEntry("设置", onNavigateToSettings)
        }

        // F6 时间选择器 + F7 领域选择器(顶部 chips)
        SelectorRow(
            label = "时间范围",
            chips = DAY_OPTIONS.map { days ->
                SelectorChip(
                    text = "${days} 天",
                    selected = state.days == days,
                    onClick = { onSelectDays(days) }
                )
            }
        )
        SelectorRow(
            label = "领域",
            chips = listOf(
                SelectorChip(
                    text = "全部",
                    selected = state.domainFilter is DomainFilter.All,
                    onClick = { onSelectDomain(DomainFilter.All) }
                )
            ) + state.availableDomains.map { domain ->
                SelectorChip(
                    text = domain.name,
                    selected = (state.domainFilter as? DomainFilter.Single)?.domainId == domain.id,
                    onClick = { onSelectDomain(DomainFilter.Single(domain.id)) }
                )
            }
        )
        LedgerRule()

        // F1 样本数 + F3 可控占比 并排
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            LedgerMetric(
                value = state.sampleCount.toString(),
                label = "本周样本",
                modifier = Modifier.weight(1f)
            )
            LedgerMetric(
                value = formatRatio(state.controllableRatio),
                label = "可控占比",
                modifier = Modifier.weight(1f),
                accent = true
            )
        }
        LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))

        // F2 高频错误前三
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionTitle("高频错误前三")
            Spacer(Modifier.height(4.dp))
            if (state.topErrors.isEmpty()) {
                EmptyHint("还没有样本")
            } else {
                state.topErrors.forEachIndexed { i, error ->
                    Surface(
                        onClick = { onNavigateToCreateTraining(error.errorTypeId) },
                        color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${i + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = MonoFamily,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Text(
                                    error.errorTypeName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Text(
                                "${error.count} 次",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = MonoFamily
                            )
                        }
                    }
                }
            }
        }
        LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))

        // F4 情绪强度最高
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SectionTitle("情绪强度最高")
            Spacer(Modifier.height(4.dp))
            val emo = state.highestEmotion
            if (emo == null) {
                EmptyHint("本周无情绪记录")
            } else {
                Text(
                    "${emo.errorTypeName}(情绪 ${emo.sample.emotionIntensity}/5)",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "建议回顾当时的触发点与身体反应",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))

        // F5 建议关注:本页唯一的强调区块
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            SectionTitle("建议关注")
            Spacer(Modifier.height(10.dp))
            val suggested = state.suggestedError
            if (suggested == null) {
                EmptyHint("本周无可控错误,继续保持")
            } else {
                Surface(
                    onClick = { onNavigateToCreateTraining(suggested.errorTypeId) },
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    NextActionBlock(
                        label = "高频可控错误",
                        text = "「${suggested.errorTypeName}」本周可控出现 ${suggested.count} 次,建议创建专项训练项。"
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

/** F3 可控占比:分母 0 或 null 显示 "—";否则 controllable/total 百分比。 */
private fun formatRatio(ratio: ControllableRatio?): String =
    if (ratio == null || ratio.total == 0) "—"
    else "${(ratio.controllable * 100 / ratio.total)}%"

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SelectorRow(label: String, chips: List<SelectorChip>) {
    // 垂直结构:小标题独占一行(对齐领域页 SectionLabel 角色),chips 独占一行流式排列。
    // 不再把 Eyebrow 与 chips 挤同一行——领域多时 chips 换行会让"两小字拖一大串"头轻脚重。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            chips.forEach { it.Content() }
        }
    }
}

/** 单个选择项:文案 + 选中态 + 点击回调。用类封装便于批量构造。 */
private class SelectorChip(
    val text: String,
    val selected: Boolean,
    val onClick: () -> Unit
) {
    @Composable
    fun Content() {
        val bg = if (selected) MaterialTheme.colorScheme.onBackground
        else MaterialTheme.colorScheme.surfaceVariant
        val fg = if (selected) MaterialTheme.colorScheme.background
        else MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            modifier = Modifier
                .background(bg)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 区块标题:13sp SemiBold + 主文本色,对齐领域页 SectionLabel,撑起分区层级。 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

/**
 * 周复盘页次级入口:描边圆角小 tag。与筛选 chips(实底填充)区分角色——
 * 导航入口用 outline 表示"可点进去",筛选项用实底表示"选中态"。
 * 去掉等宽 mono,用 sans,避免和区块标题/元信息的字族撞车。
 */
@Composable
private fun SubEntry(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

private val DAY_OPTIONS = listOf(7, 14, 30)

// ---------- Previews ----------

@Preview(name = "周复盘(有数据)", showBackground = true, heightDp = 1200)
@Composable
private fun WeeklyContentDataPreview() {
    GrowthOSTheme {
        WeeklyContent(
            state = WeeklyUiState(
                days = 7,
                domainFilter = DomainFilter.All,
                sampleCount = 8,
                topErrors = listOf(
                    ErrorTypeCount(1, "边界条件遗漏", 4),
                    ErrorTypeCount(2, "压力下急躁", 3),
                    ErrorTypeCount(3, "贪收益导致下限崩盘", 2)
                ),
                controllableRatio = ControllableRatio(total = 8, controllable = 5),
                highestEmotion = SampleWithErrorType(
                    sample = com.growthos.app.data.local.entity.Sample(
                        id = 1, domainId = 1, recordedAt = 0,
                        result = "双打输 18:21", description = "接杀急躁",
                        errorTypeId = 2, attribution = Attribution.CONTROLLABLE,
                        emotionIntensity = 4, review = "接杀先回位再选球"
                    ),
                    errorTypeName = "压力下急躁",
                    domainName = "羽毛球"
                ),
                suggestedError = ErrorTypeCount(1, "边界条件遗漏", 4),
                availableDomains = listOf(
                    Domain(id = 1, name = "编程", createdAt = 0),
                    Domain(id = 2, name = "酒馆战旗", createdAt = 1)
                )
            ),
            onSelectDays = {},
            onSelectDomain = {}
        )
    }
}

@Preview(name = "周复盘(全空)", showBackground = true, heightDp = 1000)
@Composable
private fun WeeklyContentEmptyPreview() {
    GrowthOSTheme {
        WeeklyContent(
            state = WeeklyUiState(),
            onSelectDays = {},
            onSelectDomain = {}
        )
    }
}
