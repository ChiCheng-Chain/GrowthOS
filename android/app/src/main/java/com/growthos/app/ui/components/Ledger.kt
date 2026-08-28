package com.growthos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.growthos.app.ui.theme.MonoFamily

/**
 * 账本式复用组件。整套 UI 走发丝线分隔的行,不用 Material 默认的阴影 Card,
 * 让页面读起来像一本翻开的手册而非一堆浮层。
 */

// 全宽发丝线
@Composable
fun LedgerRule(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline
) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        thickness = 0.5.dp,
        color = color
    )
}

// 区块卡片:信息分层的"块"级容器(feature 2026-08-27 信息分层改造)。
// 浅底(PaperDim)圆角、无阴影,卡内行级分隔仍用 LedgerRule;卡间由调用方留 24dp。
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 4.dp)
    ) {
        content()
    }
}

// 章节眉标:等宽小字 + 一段短线,手册式的章节起首。
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    index: String? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (index != null) {
            Text(
                text = index,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// 页面标题块:衬线大标题 + 副说明。眉标已全局取消(与底部 Tab 标签重复,2026-08-27)。
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// 大数字 + 标签:周复盘的指标行。数字用等宽,强化"账本结余"感。
@Composable
fun LedgerMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (accent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onBackground,
            fontFamily = MonoFamily
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// 行式条目:左侧主信息,右侧尾部(时间/次数等)。带底部分隔线。
@Composable
fun LedgerRow(
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.weight(1f)) { leading() }
            if (trailing != null) {
                Spacer(Modifier.width(16.dp))
                trailing()
            }
        }
        if (showDivider) {
            LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))
        }
    }
}

// "下次怎么做"区块:琥珀色左边框,强调这是面向未来的行动项。
@Composable
fun NextActionBlock(
    text: String,
    modifier: Modifier = Modifier,
    label: String = "下次怎么做"
) {
    val ochre = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val w = 3.dp.toPx()
                drawLine(
                    color = ochre,
                    start = Offset(w / 2, 0f),
                    end = Offset(w / 2, size.height),
                    strokeWidth = w
                )
            }
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

// 横向条形:错误类型分布。墨色条 + 等宽计数,不用默认图表库。
@Composable
fun DistributionBar(
    name: String,
    count: Int,
    max: Int,
    modifier: Modifier = Modifier
) {
    val ratio = if (max > 0) count.toFloat() / max else 0f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = MonoFamily
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
