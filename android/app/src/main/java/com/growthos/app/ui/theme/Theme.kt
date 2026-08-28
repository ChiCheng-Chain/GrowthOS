package com.growthos.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// 复盘手册主题:色板见 Palette.kt 五套预设(石灰纸=历史默认,逐槽锁定)。
// 强调色语义不变:accent 只用于"向前看"(下次怎么做/建议关注/可控行动),
// 其余信息一律墨色/灰阶,保持账本的克制感。
// dynamicColor(Material You)已随主题系统移除——取色器无法覆盖预设主题,语义冲突。

// 形状:手册 = 直角。除输入框圆 2dp 外,卡片、chips、按钮一律直角,强化账本感。
val LedgerShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
)

/**
 * 全局主题(feature 2026-08-28 起接 preset)。
 *
 * @param preset 预设主题,默认石灰纸(历史外观零变化);34 处既有无参调用不受影响
 * @param darkTheme 明暗跟随系统(BR-6),同一 preset 的 dark 变体
 */
@Composable
fun GrowthOSTheme(
    preset: GrowthThemePreset = GrowthThemePreset.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) preset.palette.dark else preset.palette.light
    MaterialTheme(
        colorScheme = colors.toColorScheme(dark = darkTheme),
        typography = GrowthOSTypography,
        shapes = LedgerShapes,
        content = content
    )
}
