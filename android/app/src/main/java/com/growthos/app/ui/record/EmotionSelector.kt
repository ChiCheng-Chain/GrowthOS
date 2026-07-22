package com.growthos.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.growthos.app.ui.components.Eyebrow
import com.growthos.app.ui.theme.MonoFamily

/**
 * 情绪强度 5 档点选器(R-006 / 设计 D6)。
 *
 * - 可选,默认不填(null)。点已选档清空回 null。
 * - 5 档点选保证 1~5 合法,不靠键盘输入。
 * - 未填时五档为弱样式;选中后选中档及以下用 primary 填充(进度感)。
 */
@Composable
fun EmotionSelector(
    value: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (i in 1..5) {
            val filled = value != null && i <= value
            val bg = if (filled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(bg)
            ) {
                Text(
                    text = i.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = MonoFamily,
                    color = if (filled) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (value != null) {
            Text(
                text = "点已选档清空",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "可选",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
