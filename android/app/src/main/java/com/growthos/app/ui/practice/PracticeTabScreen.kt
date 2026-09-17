package com.growthos.app.ui.practice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.growthos.app.GrowthOSApp
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.PracticeSession
import com.growthos.app.data.local.relation.PracticeSessionWithDomainName
import com.growthos.app.ui.components.Eyebrow
import com.growthos.app.ui.components.LedgerRule
import com.growthos.app.ui.components.PageHeader
import com.growthos.app.ui.components.SectionCard
import com.growthos.app.ui.theme.MonoFamily
import com.growthos.app.util.DurationFormat
import com.growthos.app.util.TimeUtil
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 练习 Tab 主页(feature 2026-09-17 / 设计 D4/D7/D8/D12,UI-Q1~Q5):
 * 计时器卡(开始→弹领域→跳动→结束弹层)→ 今日小结条 → 按天分组列表 + 补录入口。
 * 单页堆叠(UI-Q2 已确认),看板收进子页。
 */
@Composable
fun PracticeTabScreen(
    onNavigateToEdit: (Long?) -> Unit,
    onNavigateToBoard: () -> Unit
) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val vm: PracticeViewModel = viewModel(
        factory = PracticeViewModel.Factory(
            container.practiceSessionRepository,
            container.domainRepository,
            container.lastPracticeDomainStore
        )
    )
    val domains by vm.domains.collectAsStateWithLifecycle()
    val lastDomainId by vm.lastDomainId.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val dayGroups by vm.dayGroups.collectAsStateWithLifecycle()
    val todayTotalText by vm.todayTotalText.collectAsStateWithLifecycle()

    var showDomainPicker by remember { mutableStateOf(false) }
    var finishTarget by remember { mutableStateOf<PracticeSession?>(null) }

    PracticeTabContent(
        domains = domains,
        lastDomainId = lastDomainId,
        active = active,
        dayGroups = dayGroups,
        todayTotalText = todayTotalText,
        onStartClick = { showDomainPicker = true },
        onFinishClick = { active?.let { finishTarget = it } },
        onRowClick = { onNavigateToEdit(it.session.id) },
        onBoardClick = onNavigateToBoard,
        onQuickAddClick = { onNavigateToEdit(null) }
    )

    // 开始动线(UI-Q1):弹领域选择,选定即 start(DataStore 记忆在 VM 内写)。
    if (showDomainPicker) {
        DomainPickSheet(
            visible = true,
            domains = domains,
            defaultDomainId = lastDomainId ?: active?.domainId,
            onSelect = { domainId ->
                showDomainPicker = false
                vm.start(domainId)
            },
            onDismiss = { showDomainPicker = false }
        )
    }
    // 结束动线(UI-Q3):底部确认层(时长大字+起止+备注选填+修改时间入口)。
    if (finishTarget != null) {
        val target = finishTarget!!
        FinishSheet(
            session = target,
            onConfirm = { note ->
                finishTarget = null
                vm.finish(note)
            },
            onDismiss = { finishTarget = null },
            onEditTime = {
                // 修改时间入口:关闭本层,进编辑页(编辑页可改起点+时长,同一模型)
                finishTarget = null
                onNavigateToEdit(target.id)
            }
        )
    }
}

@Composable
private fun PracticeTabContent(
    domains: List<Domain>,
    lastDomainId: Long?,
    active: PracticeSession?,
    dayGroups: List<PracticeDayGroup>,
    todayTotalText: String,
    onStartClick: () -> Unit,
    onFinishClick: () -> Unit,
    onRowClick: (PracticeSessionWithDomainName) -> Unit,
    onBoardClick: () -> Unit,
    onQuickAddClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item { PageHeader(title = "练习", subtitle = "记一段投入,回看练了多少") }

        // 计时器卡(D4/UI-Q2):空闲态=开始;进行中=跳动时长+结束
        item {
            SectionCard {
                if (active == null) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "没有进行中的练习",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onStartClick,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("开始练习") }
                    }
                } else {
                    ActiveTimerCard(
                        session = active,
                        domainName = domains.firstOrNull { it.id == active.domainId }?.name ?: "未知领域",
                        onFinish = onFinishClick
                    )
                }
            }
        }

        // 小结条(UI-Q4/Q6):今日时长,点进看板
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .clickable(onClick = onBoardClick),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "今日时长",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    todayTotalText,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))
        }

        // 按天分组列表(UI-Q4)
        if (dayGroups.isEmpty()) {
            item {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "还没有练习记录。点上方「开始练习」,或补录一段时间。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onQuickAddClick) { Text("补录一段时间") }
                }
            }
        } else {
            dayGroups.forEach { group ->
                item(key = "header-${group.dayStartMillis}") {
                    DayGroupHeader(group)
                }
                items(group.rows, key = { "row-${it.session.id}" }) { row ->
                    PracticeRow(row, onClick = { onRowClick(row) })
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onQuickAddClick,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                ) { Text("补录一段时间") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 进行中卡片(D4/UI-Q2):领域名+分钟跳动时长+起点+柔和脉动点。 */
@Composable
private fun ActiveTimerCard(
    session: PracticeSession,
    domainName: String,
    onFinish: () -> Unit
) {
    // 分钟级 tick(D4):30s 重算一次 now,组合为已练时长
    var elapsedMillis by remember(session.id) {
        mutableLongStateOf(TimeUtil.nowMillis() - session.startedAt)
    }
    LaunchedEffect(session.id, session.startedAt) {
        while (true) {
            elapsedMillis = TimeUtil.nowMillis() - session.startedAt
            delay(30_000)
        }
    }
    // 柔和脉动(open Q-1):accent 点 2.4s 呼吸,克制不闪
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Column(Modifier.padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .alpha(pulse)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                domainName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            DurationFormat.formatMillis(elapsedMillis),
            style = MaterialTheme.typography.displaySmall,
            fontFamily = MonoFamily,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "开始于 ${formatTime(session.startedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        ) { Text("结束") }
    }
}

/** 日组头(UI-Q4):今天/日期 + 当日合计。 */
@Composable
private fun DayGroupHeader(group: PracticeDayGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            formatDayLabel(group.dayStartMillis),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            DurationFormat.formatMillis(group.totalMillis),
            style = MaterialTheme.typography.labelLarge,
            fontFamily = MonoFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))
}

/** 紧凑行(UI-Q4):领域点/名 + 时长 + 起止 + 备注标记,点进编辑。 */
@Composable
private fun PracticeRow(row: PracticeSessionWithDomainName, onClick: () -> Unit) {
    val session = row.session
    val endedAt = session.endedAt ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.domainName ?: "未知领域",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "${formatTime(session.startedAt)} – ${formatTime(endedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!session.note.isNullOrBlank()) {
            Text(
                "记",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 10.dp)
            )
        }
        Text(
            DurationFormat.formatMillis(endedAt - session.startedAt),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = MonoFamily,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
    LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))
}

/** 开始前领域选择弹层(UI-Q1):默认上次领域,选定即开表。 */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun DomainPickSheet(
    visible: Boolean,
    domains: List<Domain>,
    defaultDomainId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Eyebrow("选择领域,即开始计时")
            Spacer(Modifier.height(12.dp))
            if (domains.isEmpty()) {
                Text(
                    "还没有领域,请先在「领域」页创建。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                domains.forEach { domain ->
                    val selected = domain.id == defaultDomainId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(domain.id) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            domain.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onBackground
                        )
                    }
                    LedgerRule()
                }
            }
        }
    }
}

// 结束确认弹层在 FinishSheet.kt;补录/编辑页在 PracticeEditScreen.kt。

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

internal fun formatDayLabel(dayStartMillis: Long): String {
    val todayStart = TimeUtil.startOfTodayMillis()
    return when (dayStartMillis) {
        todayStart -> "今天"
        todayStart - 86_400_000L -> "昨天"
        else -> SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(dayStartMillis))
    }
}
