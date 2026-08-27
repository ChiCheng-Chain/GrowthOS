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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.growthos.app.GrowthOSApp
import com.growthos.app.data.local.entity.Training
import com.growthos.app.data.local.relation.TrainingWithNames
import com.growthos.app.domain.model.TrainingStatus
import com.growthos.app.ui.components.DomainFilterSheet
import com.growthos.app.ui.components.Eyebrow
import com.growthos.app.ui.components.FilterEntryRow
import com.growthos.app.ui.components.LedgerRule
import com.growthos.app.ui.components.NextActionBlock
import com.growthos.app.ui.theme.GrowthOSTheme
import com.growthos.app.ui.theme.MonoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 训练项列表页(二期阶段 5 / 需求 F2)。
 *
 * 全部训练项按状态+时间排序(进行中在前)。点训练项进效果页;进行中的可结束(F3 对话框)。
 * 顶部"新建"按钮进编辑页(不预填)。
 */
@Composable
fun TrainingListScreen(
    onBack: () -> Unit,
    onOpenEffect: (Long) -> Unit,
    onOpenCreate: () -> Unit
) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val vm: TrainingListViewModel = viewModel(
        factory = TrainingListViewModel.Factory(container.trainingRepository, container.domainRepository)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    var filterSheetOpen by remember { mutableStateOf(false) }

    TrainingListContent(
        state = state,
        onBack = onBack,
        onOpenEffect = onOpenEffect,
        onOpenCreate = onOpenCreate,
        onFinish = vm::finishTraining,
        onDelete = vm::deleteTraining,
        onOpenFilter = { filterSheetOpen = true }
    )

    // 领域筛选弹层(feature 2026-08-27 BR-3):选中即时生效。
    DomainFilterSheet(
        visible = filterSheetOpen,
        title = "筛选训练项",
        domainFilter = state.domainFilter,
        availableDomains = state.availableDomains,
        onDomainSelect = vm::filterByDomain,
        onDismiss = { filterSheetOpen = false }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TrainingListContent(
    state: TrainingListUiState,
    onBack: () -> Unit,
    onOpenEffect: (Long) -> Unit,
    onOpenCreate: () -> Unit,
    onFinish: (Long, TrainingStatus) -> Unit,
    onDelete: (Long) -> Unit,
    onOpenFilter: () -> Unit = {}
) {
    var finishing by remember { mutableStateOf<TrainingWithNames?>(null) }
    var deleting by remember { mutableStateOf<TrainingWithNames?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("训练项", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenCreate) {
                        Icon(Icons.Outlined.Add, contentDescription = "新建训练项")
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
            // 领域筛选入口行(feature 2026-08-27 BR-3)
            val activeDomain = state.availableDomains.firstOrNull { it.id == state.domainFilter }
            FilterEntryRow(
                countText = "${state.filteredTrainings.size} 项",
                activeLabel = activeDomain?.name,
                onOpenFilter = onOpenFilter
            )

            if (state.isEmpty) {
                EmptyHint()
            } else {
                state.filteredTrainings.forEach { item ->
                    TrainingRow(
                        item = item,
                        onClick = { onOpenEffect(item.training.id) },
                        onFinish = { finishing = item },
                        onDelete = { deleting = item }
                    )
                    LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    // F3 结束对话框(D3):二选一
    finishing?.let { item ->
        AlertDialog(
            onDismissRequest = { finishing = null },
            title = { Text("结束训练项") },
            text = { Text("「${item.errorTypeName}」的结束方式?结束后归档,不可再改。") },
            confirmButton = {
                TextButton(onClick = {
                    onFinish(item.training.id, TrainingStatus.COMPLETED)
                    finishing = null
                }) { Text("已完成") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { finishing = null }) { Text("取消") }
                    TextButton(onClick = {
                        onFinish(item.training.id, TrainingStatus.ABANDONED)
                        finishing = null
                    }) { Text("已放弃", color = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }

    // 物理删除确认(CRUD 补全):已结束训练项可删
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除训练项") },
            text = { Text("删除「${item.errorTypeName}」的训练项?不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(item.training.id)
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun TrainingRow(
    item: TrainingWithNames,
    onClick: () -> Unit,
    onFinish: () -> Unit,
    onDelete: () -> Unit
) {
    val t = item.training
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
                    "${item.errorTypeName} · ${item.domainName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    t.status.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (t.status == TrainingStatus.IN_PROGRESS) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = MonoFamily
                )
            }
            Text(
                t.goal,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (t.endedAt != null) "开始 ${formatDate(t.startedAt)} · 结束 ${formatDate(t.endedAt)}"
                else "开始 ${formatDate(t.startedAt)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = MonoFamily
            )
            if (t.status == TrainingStatus.IN_PROGRESS) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onFinish, modifier = Modifier.padding(start = 0.dp)) {
                    Text("结束", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                // 已结束(完成/放弃):可物理删除(CRUD 补全)
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDelete, modifier = Modifier.padding(start = 0.dp)) {
                    Text("删除", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Eyebrow("还没有训练项")
        Spacer(Modifier.height(10.dp))
        NextActionBlock(
            label = "开始",
            text = "从周复盘的高频错误开始,建第一个训练项。在周复盘页点「建议关注」或高频错误前三,即可带错误类型预填进入新建。"
        )
    }
}

private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
private fun formatDate(epochMillis: Long): String = dateFmt.format(Date(epochMillis))

// ---------- Previews ----------

private val previewTraining = Training(
    id = 1, domainId = 1, errorTypeId = 1, goal = "涉及分支/边界先列清单再写代码",
    acceptanceCriteria = "连续 3 次不漏边界", startedAt = 0, endedAt = null,
    status = TrainingStatus.IN_PROGRESS, note = null
)
private val previewFinished = previewTraining.copy(
    id = 2, endedAt = 1000000L, status = TrainingStatus.COMPLETED
)

@Preview(name = "训练项列表(有数据)", showBackground = true, heightDp = 700)
@Composable
private fun TrainingListDataPreview() {
    GrowthOSTheme {
        TrainingListContent(
            state = TrainingListUiState(
                trainings = listOf(
                    TrainingWithNames(previewTraining, "边界条件遗漏", "编程"),
                    TrainingWithNames(previewFinished, "压力下急躁", "羽毛球")
                )
            ),
            onBack = {}, onOpenEffect = {}, onOpenCreate = {}, onFinish = { _, _ -> }, onDelete = {}
        )
    }
}

@Preview(name = "训练项列表(空)", showBackground = true, heightDp = 500)
@Composable
private fun TrainingListEmptyPreview() {
    GrowthOSTheme {
        TrainingListContent(
            state = TrainingListUiState(),
            onBack = {}, onOpenEffect = {}, onOpenCreate = {}, onFinish = { _, _ -> }, onDelete = {}
        )
    }
}
