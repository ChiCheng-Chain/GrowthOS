package com.growthos.app.ui.domain_view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.growthos.app.GrowthOSApp
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.Knowledge
import com.growthos.app.data.local.entity.Principle
import com.growthos.app.data.local.relation.ErrorTypeCount
import com.growthos.app.data.local.relation.SampleWithErrorType
import com.growthos.app.data.local.relation.TrainingWithTypeName
import com.growthos.app.domain.model.Attribution
import com.growthos.app.ui.components.DistributionBar
import com.growthos.app.ui.components.Eyebrow
import com.growthos.app.ui.components.LedgerRule
import com.growthos.app.ui.components.NextActionBlock
import com.growthos.app.ui.components.PageHeader
import com.growthos.app.ui.domain.DomainDialog
import com.growthos.app.ui.domain.DomainEditDialog
import com.growthos.app.ui.domain.DomainUiState
import com.growthos.app.ui.domain.DomainViewModel
import com.growthos.app.ui.theme.GrowthOSTheme
import com.growthos.app.ui.theme.MonoFamily
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 领域页 — 产品定位 8.2 / R-008:最近样本、错误类型分布、当前训练项、近期原则。
 *
 * 阶段 1:领域 chips 接真数据(新建/改名/隐藏/恢复/选中持久化)。
 * 阶段 3:四区块接 [DomainStatsViewModel] 真聚合数据 + F5 样本筛选(R-007)。
 */
@Composable
fun DomainScreen(
    onOpenSample: (Long) -> Unit = {},
    onNavigateToEffect: (Long) -> Unit = {},
    onNavigateToPrincipleEdit: (Long) -> Unit = {},
    onNavigateToKnowledgeEdit: (Long) -> Unit = {}
) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val domainVm: DomainViewModel = viewModel(
        factory = DomainViewModel.Factory(container.domainRepository, container.selectedDomainStore)
    )
    val statsVm: DomainStatsViewModel = viewModel(
        factory = DomainStatsViewModel.Factory(
            container.sampleRepository,
            container.trainingRepository,
            container.principleRepository,
            container.knowledgeRepository,
            container.selectedDomainStore
        )
    )
    val domainState by domainVm.uiState.collectAsStateWithLifecycle()
    val statsState by statsVm.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    DomainContent(
        domainState = domainState,
        statsState = statsState,
        scrollState = scrollState,
        hasDuplicate = domainVm::hasDuplicate,
        onSelect = domainVm::select,
        onOpenCreate = domainVm::openCreate,
        onOpenEdit = domainVm::openEdit,
        onSave = { name, hidden ->
            val dialog = domainState.dialog
            when (dialog) {
                is DomainDialog.Create -> domainVm.create(name)
                is DomainDialog.Edit -> {
                    domainVm.rename(dialog.domain.id, name)
                    if (dialog.domain.hidden != hidden) domainVm.setHidden(dialog.domain.id, hidden)
                }
                null -> {}
            }
        },
        onUnhide = domainVm::unhide,
        onDismissDialog = domainVm::dismissDialog,
        onOpenSample = onOpenSample,
        onNavigateToEffect = onNavigateToEffect,
        onNavigateToPrincipleEdit = onNavigateToPrincipleEdit,
        onNavigateToKnowledgeEdit = onNavigateToKnowledgeEdit,
        onFilterErrorType = { id ->
            statsVm.filterByErrorType(id)
            scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
        },
        onFilterAttribution = statsVm::filterByAttribution,
        onClearFilter = statsVm::clearFilter
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
fun DomainContent(
    domainState: DomainUiState,
    statsState: DomainStatsUiState,
    scrollState: androidx.compose.foundation.ScrollState,
    hasDuplicate: (String, Long?) -> Boolean,
    onSelect: (Long) -> Unit,
    onOpenCreate: () -> Unit,
    onOpenEdit: (Domain) -> Unit,
    onSave: (name: String, hidden: Boolean) -> Unit,
    onUnhide: (Long) -> Unit,
    onDismissDialog: () -> Unit,
    onOpenSample: (Long) -> Unit,
    onNavigateToEffect: (Long) -> Unit,
    onNavigateToPrincipleEdit: (Long) -> Unit,
    onNavigateToKnowledgeEdit: (Long) -> Unit,
    onFilterErrorType: (Long?) -> Unit,
    onFilterAttribution: (Attribution?) -> Unit,
    onClearFilter: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
    ) {
        PageHeader(
            eyebrow = "领域",
            title = domainState.selectedDomain?.name ?: "领域",
            subtitle = if (domainState.selectedDomain != null) "选中领域 · 最近样本 / 错误分布 / 训练项 / 原则"
            else "先建一个领域,开始记录与复盘"
        )

        if (domainState.isEmpty) {
            EmptyState(onOpenCreate = onOpenCreate)
        } else {
            // 切换 chips:"切换"眉标与领域 chips + 新增按钮同处一行,同高对齐。
            // Eyebrow 补 vertical padding 与 chips(labelLarge + 8dp)等高。
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Eyebrow("切换", modifier = Modifier.padding(vertical = 8.dp))
                domainState.domains.forEach { domain ->
                    DomainChip(
                        name = domain.name,
                        selected = domain.id == domainState.selectedId,
                        onClick = { onSelect(domain.id) },
                        onLongClick = { onOpenEdit(domain) }
                    )
                }
                NewDomainChip(onClick = onOpenCreate)
            }
        }
        LedgerRule()

        // 已隐藏折叠区
        if (domainState.hiddenDomains.isNotEmpty()) {
            HiddenSection(
                hidden = domainState.hiddenDomains,
                selectedId = domainState.selectedId,
                onUnhide = onUnhide,
                onOpenEdit = onOpenEdit
            )
            LedgerRule()
        }

        // 四区块(仅有选中领域时展示)
        if (domainState.selectedDomain != null) {
            // F1 最近样本
            SectionLabel("最近样本")
            if (statsState.recentSamples.isEmpty()) {
                EmptyHint("还没有样本,去记录一条")
            } else {
                statsState.recentSamples.forEach { row ->
                    SampleStatRow(row = row, onClick = { onOpenSample(row.sample.id) })
                }
            }
            LedgerRule(modifier = Modifier.padding(top = 8.dp))

            // F2 错误类型分布(点条触发筛选 → 设计 D5)
            SectionLabel("错误类型分布")
            if (statsState.errorDistribution.isEmpty()) {
                EmptyHint("还没有样本")
            } else {
                val max = statsState.errorDistribution.maxOf { it.count }
                statsState.errorDistribution.forEach { et ->
                    DistributionBar(
                        name = et.errorTypeName,
                        count = et.count,
                        max = max,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .combinedClickable { onFilterErrorType(et.errorTypeId) }
                    )
                }
            }
            LedgerRule(modifier = Modifier.padding(top = 8.dp))

            // F3 当前训练项
            SectionLabel("当前训练项")
            if (statsState.inProgressTrainings.isEmpty()) {
                EmptyHint("还没有训练项(阶段 5 可建)")
            } else {
                statsState.inProgressTrainings.forEach { t ->
                    Surface(
                        onClick = { onNavigateToEffect(t.training.id) },
                        color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ) {
                        NextActionBlock(
                            label = "训练中 · ${t.errorTypeName}",
                            text = t.training.goal
                        )
                    }
                }
            }
            LedgerRule()

            // F4 近期原则(阶段 6:可点进编辑,D4)
            SectionLabel("近期原则")
            if (statsState.recentPrinciples.isEmpty()) {
                EmptyHint("还没有原则,去原则库新建")
            } else {
                statsState.recentPrinciples.forEach { p ->
                    PrincipleRow(p) { onNavigateToPrincipleEdit(p.id) }
                }
            }
            LedgerRule()

            // 近期知识(外部摄取:经验/待办,可点进编辑)
            SectionLabel("近期知识")
            if (statsState.recentKnowledges.isEmpty()) {
                EmptyHint("还没有知识,去知识库新建")
            } else {
                statsState.recentKnowledges.forEach { k ->
                    KnowledgeMiniRow(k) { onNavigateToKnowledgeEdit(k.id) }
                }
            }
            LedgerRule()

            // F5 筛选条 + 样本列表
            SectionLabel("样本列表")
            SampleFilterBar(
                availableErrorTypes = statsState.availableErrorTypes,
                filter = statsState.filter,
                onErrorTypeSelect = onFilterErrorType,
                onAttributionSelect = onFilterAttribution,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            LedgerRule(modifier = Modifier.padding(top = 8.dp))
            if (statsState.filteredSamples.isEmpty()) {
                EmptyHint(
                    if (statsState.filter.errorTypeId == null && statsState.filter.attribution == null)
                        "还没有样本"
                    else "没有符合条件的样本"
                )
            } else {
                statsState.filteredSamples.forEach { row ->
                    SampleStatRow(row = row, onClick = { onOpenSample(row.sample.id) })
                }
            }
            LedgerRule()
        }

        Spacer(Modifier.height(32.dp))
    }

    // 对话框(阶段 1)
    val dialog = domainState.dialog
    if (dialog != null) {
        val excludeId = (dialog as? DomainDialog.Edit)?.domain?.id
        DomainEditDialog(
            dialog = dialog,
            hasDuplicate = { name -> hasDuplicate(name, excludeId) },
            onSave = onSave,
            onDismiss = onDismissDialog
        )
    }
}

@Composable
private fun SampleStatRow(row: SampleWithErrorType, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            NextActionBlock(text = row.sample.review)
        }
    }
    LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun PrincipleRow(principle: Principle, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                principle.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatDate(principle.createdAt),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = MonoFamily
            )
        }
    }
    LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun KnowledgeMiniRow(knowledge: Knowledge, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    knowledge.type.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = MonoFamily
                )
                Text(
                    formatDate(knowledge.createdAt),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = MonoFamily
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                knowledge.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
    LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun EmptyState(onOpenCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Eyebrow("还没有领域")
        Spacer(Modifier.height(10.dp))
        Surface(
            onClick = onOpenCreate,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.background
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "新建第一个领域",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "领域是样本的归属。先起一个,比如“编程”。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.background
                    )
                }
                Icon(Icons.Outlined.Add, contentDescription = "新建")
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DomainChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.onBackground
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.background
    else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = name,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        modifier = Modifier
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun NewDomainChip(onClick: () -> Unit) {
    // 与 DomainChip 同构(Text + background + padding),保证同行高度一致。
    Text(
        text = "+ 新建",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HiddenSection(
    hidden: List<Domain>,
    selectedId: Long?,
    onUnhide: (Long) -> Unit,
    onOpenEdit: (Domain) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Eyebrow("已隐藏(${hidden.size})")
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            hidden.forEach { domain ->
                HiddenChip(
                    name = domain.name,
                    onRestore = { onUnhide(domain.id) },
                    onEdit = { onOpenEdit(domain) }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HiddenChip(
    name: String,
    onRestore: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "恢复",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.combinedClickable(onClick = onRestore)
        )
        Text(
            "编辑",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.combinedClickable(onClick = onEdit)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Eyebrow(text)
        Spacer(Modifier.height(10.dp))
    }
}

private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
private fun formatTime(epochMillis: Long): String = timeFmt.format(Date(epochMillis))
private fun formatDate(epochMillis: Long): String = dateFmt.format(Date(epochMillis))

// ---------- Previews ----------

private val sampleDomain = Domain(id = 1, name = "编程", createdAt = 0, hidden = false)

@Preview(name = "领域页(有数据)", showBackground = true, heightDp = 1400)
@Composable
private fun DomainContentPreview() {
    GrowthOSTheme {
        DomainContent(
            domainState = DomainUiState(
                domains = listOf(
                    sampleDomain,
                    Domain(id = 2, name = "酒馆战旗", createdAt = 1, hidden = false)
                ),
                selectedId = 1
            ),
            statsState = DomainStatsUiState(
                recentSamples = listOf(
                    SampleWithErrorType(
                        sample = com.growthos.app.data.local.entity.Sample(
                            id = 1, domainId = 1, recordedAt = System.currentTimeMillis(),
                            result = "线上 bug", description = "退款分支",
                            errorTypeId = 1, attribution = Attribution.CONTROLLABLE,
                            emotionIntensity = 4, review = "先列状态表"
                        ),
                        errorTypeName = "边界条件遗漏", domainName = "编程"
                    )
                ),
                errorDistribution = listOf(
                    ErrorTypeCount(1, "边界条件遗漏", 4),
                    ErrorTypeCount(2, "压力下急躁", 3)
                ),
                inProgressTrainings = listOf(
                    TrainingWithTypeName(
                        training = com.growthos.app.data.local.entity.Training(
                            id = 1, domainId = 1, errorTypeId = 1, goal = "先列状态表再写代码",
                            acceptanceCriteria = null, startedAt = 0, endedAt = null,
                            status = com.growthos.app.domain.model.TrainingStatus.IN_PROGRESS, note = null
                        ),
                        errorTypeName = "边界条件遗漏"
                    )
                ),
                recentPrinciples = listOf(
                    Principle(id = 1, content = "边界先列清单", createdAt = 0, domainId = 1)
                ),
                filteredSamples = emptyList(),
                hasDomain = true
            ),
            scrollState = rememberScrollState(),
            hasDuplicate = { _, _ -> false },
            onSelect = {}, onOpenCreate = {}, onOpenEdit = {},
            onSave = { _, _ -> }, onUnhide = {}, onDismissDialog = {},
            onOpenSample = {}, onNavigateToEffect = {}, onNavigateToPrincipleEdit = {},
            onNavigateToKnowledgeEdit = {},
            onFilterErrorType = {}, onFilterAttribution = {},
            onClearFilter = {}
        )
    }
}
