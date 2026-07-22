package com.growthos.app.ui.principle

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
import com.growthos.app.data.local.entity.Principle
import com.growthos.app.data.local.relation.PrincipleWithNames
import com.growthos.app.ui.components.Eyebrow
import com.growthos.app.ui.components.LedgerRule
import com.growthos.app.ui.components.NextActionBlock
import com.growthos.app.ui.theme.GrowthOSTheme
import com.growthos.app.ui.theme.MonoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 原则列表页(二期阶段 6 / 需求 F2)。
 *
 * 全部原则按 createdAt 倒序。点原则进编辑页;可删(F3 确认)。顶部「新建」按钮进编辑页。
 */
@Composable
fun PrincipleListScreen(
    onBack: () -> Unit,
    onOpenEdit: (Long?) -> Unit,
    onOpenCreate: () -> Unit
) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val vm: PrincipleListViewModel = viewModel(
        factory = PrincipleListViewModel.Factory(container.principleRepository)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    PrincipleListContent(
        state = state,
        onBack = onBack,
        onOpenEdit = onOpenEdit,
        onOpenCreate = onOpenCreate,
        onDelete = vm::delete
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PrincipleListContent(
    state: PrincipleListUiState,
    onBack: () -> Unit,
    onOpenEdit: (Long?) -> Unit,
    onOpenCreate: () -> Unit,
    onDelete: (Principle) -> Unit
) {
    var deleting by remember { mutableStateOf<PrincipleWithNames?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("原则库", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenCreate) {
                        Icon(Icons.Outlined.Add, contentDescription = "新建原则")
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
            if (state.isEmpty) {
                EmptyHint()
            } else {
                state.principles.forEach { item ->
                    PrincipleRow(
                        item = item,
                        onClick = { onOpenEdit(item.principle.id) },
                        onDelete = { deleting = item }
                    )
                    LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    // F3 删除确认(D5):原则是末端对象,直接确认删。
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除原则") },
            text = { Text("删除「${item.principle.content.take(20)}${if (item.principle.content.length > 20) "…" else ""}」?不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(item.principle)
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
private fun PrincipleRow(
    item: PrincipleWithNames,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val p = item.principle
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
            // 关联名(领域·错误类型,缺失则隐藏,D5 容错)
            val relation = listOfNotNull(item.domainName, item.errorTypeName).joinToString(" · ")
            if (relation.isNotEmpty()) {
                Text(
                    relation,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                p.content,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                formatDate(p.createdAt),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = MonoFamily
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDelete, modifier = Modifier.padding(start = 0.dp)) {
                Text("删除", style = MaterialTheme.typography.labelLarge)
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
        Eyebrow("还没有原则")
        Spacer(Modifier.height(10.dp))
        NextActionBlock(
            label = "开始",
            text = "从最近的复盘中,把可迁移的认知写下来。原则是跨样本跨周期的沉淀,下次遇到同类情境能回看提醒自己。"
        )
    }
}

private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
private fun formatDate(epochMillis: Long): String = dateFmt.format(Date(epochMillis))

// ---------- Previews ----------

private val previewPrinciple = Principle(
    id = 1, content = "涉及分支/边界先列清单再写代码", createdAt = 1000000L,
    domainId = 1, errorTypeId = 1
)
private val previewPrinciple2 = Principle(
    id = 2, content = "压力下先回位再选球,不抢抽", createdAt = 2000000L,
    domainId = 2, errorTypeId = 2
)
private val previewPrincipleNoRelation = Principle(
    id = 3, content = "每周复盘一次,不积压", createdAt = 3000000L
)

@Preview(name = "原则库(有数据)", showBackground = true, heightDp = 700)
@Composable
private fun PrincipleListDataPreview() {
    GrowthOSTheme {
        PrincipleListContent(
            state = PrincipleListUiState(
                principles = listOf(
                    PrincipleWithNames(previewPrinciple, "编程", "边界条件遗漏"),
                    PrincipleWithNames(previewPrinciple2, "羽毛球", "压力下急躁"),
                    PrincipleWithNames(previewPrincipleNoRelation, null, null)
                )
            ),
            onBack = {}, onOpenEdit = {}, onOpenCreate = {}, onDelete = {}
        )
    }
}

@Preview(name = "原则库(空)", showBackground = true, heightDp = 500)
@Composable
private fun PrincipleListEmptyPreview() {
    GrowthOSTheme {
        PrincipleListContent(
            state = PrincipleListUiState(),
            onBack = {}, onOpenEdit = {}, onOpenCreate = {}, onDelete = {}
        )
    }
}
