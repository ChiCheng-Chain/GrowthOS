package com.growthos.app.ui.knowledge

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.growthos.app.GrowthOSApp
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.Knowledge
import com.growthos.app.data.local.relation.KnowledgeWithDomainName
import com.growthos.app.domain.model.KnowledgeType
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
 * 知识库列表页。对齐 [com.growthos.app.ui.principle.PrincipleListScreen] 范式。
 *
 * 全部知识按 createdAt 倒序。点知识进编辑页;待办可点圆圈切换完成;
 * 可删(末端对象,直接确认)。顶部「新建」进编辑页。
 */
@Composable
fun KnowledgeListScreen(
    onBack: () -> Unit,
    onOpenEdit: (Long?) -> Unit,
    onOpenCreate: () -> Unit
) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val vm: KnowledgeListViewModel = viewModel(
        factory = KnowledgeListViewModel.Factory(
            container.knowledgeRepository,
            container.domainRepository
        )
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    var filterSheetOpen by remember { mutableStateOf(false) }

    KnowledgeListContent(
        state = state,
        onBack = onBack,
        onOpenEdit = onOpenEdit,
        onOpenCreate = onOpenCreate,
        onDelete = vm::delete,
        onToggleDone = vm::toggleDone,
        onFilterDomain = vm::filterByDomain,
        onOpenFilter = { filterSheetOpen = true }
    )

    // 领域筛选弹层(feature 2026-08-27 BR-4):复用既有 filterByDomain,选中即时生效。
    DomainFilterSheet(
        visible = filterSheetOpen,
        title = "筛选知识",
        domainFilter = state.domainFilter,
        availableDomains = state.domains,
        onDomainSelect = vm::filterByDomain,
        onDismiss = { filterSheetOpen = false }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun KnowledgeListContent(
    state: KnowledgeListUiState,
    onBack: () -> Unit,
    onOpenEdit: (Long?) -> Unit,
    onOpenCreate: () -> Unit,
    onDelete: (Knowledge) -> Unit,
    onToggleDone: (Knowledge) -> Unit,
    onFilterDomain: (Long?) -> Unit,
    onOpenFilter: () -> Unit = {}
) {
    var deleting by remember { mutableStateOf<KnowledgeWithDomainName?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("知识库", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenCreate) {
                        Icon(Icons.Outlined.Add, contentDescription = "新建知识")
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
            // 领域筛选入口行(feature 2026-08-27 BR-3/BR-4):常驻条改弹层,统一三列表页交互
            val activeDomain = state.domains.firstOrNull { it.id == state.domainFilter }
            FilterEntryRow(
                countText = "${state.filteredKnowledges.size} 条",
                activeLabel = activeDomain?.name,
                onOpenFilter = onOpenFilter
            )

            if (state.isEmpty) {
                EmptyHint()
            } else {
                state.filteredKnowledges.forEach { item ->
                    KnowledgeRow(
                        item = item,
                        onClick = { onOpenEdit(item.knowledge.id) },
                        onToggleDone = { onToggleDone(item.knowledge) },
                        onDelete = { deleting = item }
                    )
                    LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    // 删除确认:知识是末端对象,直接确认删(对齐 Principle 范式)
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除知识") },
            text = { Text("删除「${item.knowledge.content.take(20)}${if (item.knowledge.content.length > 20) "…" else ""}」?不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(item.knowledge)
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
private fun KnowledgeRow(
    item: KnowledgeWithDomainName,
    onClick: () -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit
) {
    val k = item.knowledge
    val isTodo = k.type == KnowledgeType.TODO
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 待办类型:完成状态圆圈,可点切换
                    if (isTodo) {
                        IconButton(onClick = onToggleDone, modifier = Modifier.padding(end = 4.dp)) {
                            Icon(
                                if (k.done) Icons.Outlined.CheckCircle else Icons.Outlined.Circle,
                                contentDescription = if (k.done) "标记未完成" else "标记完成",
                                tint = if (k.done) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // 类型标签
                    Text(
                        k.type.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isTodo && k.done) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                        fontFamily = MonoFamily
                    )
                    item.domainName?.let { name ->
                        Text(
                            " · $name",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    formatDate(k.createdAt),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = MonoFamily
                )
            }
            Text(
                k.content,
                style = MaterialTheme.typography.titleMedium,
                color = if (isTodo && k.done) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (isTodo && k.done) TextDecoration.LineThrough else TextDecoration.None
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDelete, modifier = Modifier.padding(start = 0.dp)) {
                Text("删除", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 领域筛选条(已改弹层形态,此组件保留给 Preview 使用场景已移除;删除)。 */
@Composable
private fun EmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Eyebrow("还没有知识")
        Spacer(Modifier.height(10.dp))
        NextActionBlock(
            label = "开始",
            text = "从视频、经验贴或日常思考中,把值得记住的知识按领域记下来。经验是「知道的事」,待办是「该做的事」,与你自己实践沉淀的原则互补。"
        )
    }
}

private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
private fun formatDate(epochMillis: Long): String = dateFmt.format(Date(epochMillis))

// ---------- Previews ----------

private val previewExperience = Knowledge(
    id = 1, content = "双打接杀要先回位再选球,不要抢抽", type = KnowledgeType.EXPERIENCE,
    createdAt = 1000000L, domainId = 1
)
private val previewTodo = Knowledge(
    id = 2, content = "下周试试新的发球站位", type = KnowledgeType.TODO,
    createdAt = 2000000L, domainId = 1, done = false
)
private val previewTodoDone = Knowledge(
    id = 3, content = "看完那篇关于异步编程的帖子", type = KnowledgeType.TODO,
    createdAt = 3000000L, domainId = 2, done = true
)

@Preview(name = "知识库(有数据)", showBackground = true, heightDp = 700)
@Composable
private fun KnowledgeListDataPreview() {
    GrowthOSTheme {
        KnowledgeListContent(
            state = KnowledgeListUiState(
                knowledges = listOf(
                    KnowledgeWithDomainName(previewTodoDone, "编程"),
                    KnowledgeWithDomainName(previewTodo, "羽毛球"),
                    KnowledgeWithDomainName(previewExperience, "羽毛球")
                )
            ),
            onBack = {}, onOpenEdit = {}, onOpenCreate = {},
            onDelete = {}, onToggleDone = {}, onFilterDomain = {}
        )
    }
}

@Preview(name = "知识库(空)", showBackground = true, heightDp = 500)
@Composable
private fun KnowledgeListEmptyPreview() {
    GrowthOSTheme {
        KnowledgeListContent(
            state = KnowledgeListUiState(),
            onBack = {}, onOpenEdit = {}, onOpenCreate = {},
            onDelete = {}, onToggleDone = {}, onFilterDomain = {}
        )
    }
}
