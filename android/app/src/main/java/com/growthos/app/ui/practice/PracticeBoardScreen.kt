package com.growthos.app.ui.practice

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.growthos.app.GrowthOSApp
import com.growthos.app.data.local.dao.PracticeDomainTotal
import com.growthos.app.ui.components.DistributionBar
import com.growthos.app.ui.components.Eyebrow
import com.growthos.app.ui.components.LedgerRule
import com.growthos.app.ui.components.PageHeader
import com.growthos.app.ui.components.SectionCard
import com.growthos.app.ui.theme.MonoFamily
import com.growthos.app.ui.weekly.DomainFilter
import com.growthos.app.ui.weekly.WeeklyScopeSheet
import com.growthos.app.util.DurationFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 练习看板子页(设计 D9/D10/D11):口径(天数+领域)→ 总时长+场均 → 领域分布 → 按天趋势。
 * 完全沿用 ledger 视觉语言(BR-10),墨色条形 + 等宽数字,无新配色。
 */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun PracticeBoardScreen(
    onBack: () -> Unit
) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val vm: PracticeBoardViewModel = viewModel(
        factory = PracticeBoardViewModel.Factory(
            container.practiceSessionRepository,
            container.domainRepository
        )
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val domains by vm.domains.collectAsStateWithLifecycle()
    var showScopeSheet by remember { mutableStateOf(false) }

    val filterLabel = when (val f = state.domainFilter) {
        DomainFilter.All -> "全部领域"
        is DomainFilter.Single -> domains.firstOrNull { it.id == f.domainId }?.name ?: "单领域"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("练习看板", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // 口径入口(可点摘要,对齐周复盘范式)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showScopeSheet = true }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "最近 ${state.days} 天 · $filterLabel · ${state.count} 次",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "调整",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LedgerRule()

            // ① 总时长 + 场均(等宽大数字)
            SectionCard(Modifier.padding(top = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Eyebrow("总时长")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.totalText,
                            style = MaterialTheme.typography.displaySmall,
                            fontFamily = MonoFamily,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Eyebrow("场均")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.averageText,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = MonoFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ② 按领域分布(复用 DistributionBar,墨色条)
            Column(Modifier.padding(horizontal = 20.dp)) {
                Eyebrow("按领域")
                Spacer(Modifier.height(8.dp))
                if (state.domainTotals.isEmpty()) {
                    EmptyBoardHint()
                } else {
                    val max = state.domainTotals.maxOf { it.totalMillis }
                    state.domainTotals.forEach { item ->
                        DomainTotalBar(item, max)
                    }
                }
                Spacer(Modifier.height(16.dp))
                LedgerRule()
            }
            Spacer(Modifier.height(16.dp))

            // ③ 按天趋势(VM 内存分桶,竖向微型条形序列)
            Column(Modifier.padding(horizontal = 20.dp)) {
                Eyebrow("按天趋势")
                Spacer(Modifier.height(12.dp))
                if (state.dailyTotals.isEmpty()) {
                    EmptyBoardHint()
                } else {
                    DailyTrendRow(state.dailyTotals, state.days)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    // 口径弹层(复用 WeeklyScopeSheet,标题参数化「练习口径」,D11)
    WeeklyScopeSheet(
        visible = showScopeSheet,
        days = state.days,
        domainFilter = state.domainFilter,
        availableDomains = domains,
        onSelectDays = vm::selectDays,
        onSelectDomain = vm::selectDomain,
        onDismiss = { showScopeSheet = false },
        title = "练习口径"
    )
}

@Composable
private fun DomainTotalBar(item: PracticeDomainTotal, maxMillis: Long) {
    val ratio = if (maxMillis > 0) item.totalMillis.toFloat() / maxMillis else 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                item.domainName ?: "未知领域",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                DurationFormat.formatMillis(item.totalMillis),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = MonoFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth(ratio)
                .height(6.dp)
                .background(MaterialTheme.colorScheme.onBackground, MaterialTheme.shapes.small)
        )
    }
}

/** 按天趋势:窗口内每天一列,竖条高度按时长比例(窗口首日→今天,左→右)。 */
@Composable
private fun DailyTrendRow(dailyTotals: List<PracticeDailyTotal>, days: Int) {
    val today = com.growthos.app.util.TimeUtil.startOfTodayMillis()
    val dayMillis = 86_400_000L
    val maxMillis = dailyTotals.maxOf { it.totalMillis }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        (days - 1 downTo 0).forEach { offset ->
            val day = today - offset * dayMillis
            val total = dailyTotals.firstOrNull { it.dayStartMillis == day }?.totalMillis ?: 0L
            val ratio = if (maxMillis > 0) total.toFloat() / maxMillis else 0f
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 条形高度 0~72dp,零值给 2dp 底线
                Box(
                    Modifier
                        .width(14.dp)
                        .height((6 + 66 * ratio).dp)
                        .background(
                            if (total > 0) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.small
                        )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    SimpleDateFormat("d", Locale.getDefault()).format(Date(day)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyBoardHint() {
    Text(
        "这段窗口里还没有练习记录。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}
