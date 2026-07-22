package com.growthos.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// 复盘手册配色:石灰纸底 + 墨黑文字 + 单一琥珀强调色。
// 琥珀(Ochre)只用于"向前看"的语义——下次怎么做、建议关注、可控行动;
// 其余信息一律墨黑/灰阶,保持账本的克制感。
// Android 12+ 默认走 dynamicColor,可在设置里关掉回到这套品牌色。

private val Paper = Color(0xFFF4F1EA)
private val PaperDim = Color(0xFFECE7DB)
private val Ink = Color(0xFF1A1814)
private val InkSoft = Color(0xFF4A463E)
private val InkFaint = Color(0xFF8B8578)
private val Ochre = Color(0xFFB5651D)
private val OchreSoft = Color(0xFFE8D4B8)
private val Rule = Color(0xFFD9D2C2)

private val InkDark = Color(0xFFE8E3D6)
private val InkSoftDark = Color(0xFFB8B0A0)
private val PaperDark = Color(0xFF13110D)
private val PaperDimDark = Color(0xFF1C1914)
private val RuleDark = Color(0xFF2E2A22)

private val LightColors = lightColorScheme(
    primary = Ochre,
    onPrimary = Color.White,
    primaryContainer = OchreSoft,
    onPrimaryContainer = Ink,
    secondary = InkSoft,
    onSecondary = Paper,
    secondaryContainer = PaperDim,
    onSecondaryContainer = Ink,
    tertiary = Ochre,
    onTertiary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDim,
    onSurfaceVariant = InkSoft,
    surfaceTint = Ochre,
    outline = Rule,
    outlineVariant = Rule,
    inverseSurface = Ink,
    inverseOnSurface = Paper
)

private val DarkColors = darkColorScheme(
    primary = Ochre,
    onPrimary = Ink,
    primaryContainer = Color(0xFF5A3A0E),
    onPrimaryContainer = OchreSoft,
    secondary = InkSoftDark,
    onSecondary = PaperDark,
    secondaryContainer = PaperDimDark,
    onSecondaryContainer = InkDark,
    tertiary = Ochre,
    onTertiary = Ink,
    background = PaperDark,
    onBackground = InkDark,
    surface = PaperDark,
    onSurface = InkDark,
    surfaceVariant = PaperDimDark,
    onSurfaceVariant = InkSoftDark,
    surfaceTint = Ochre,
    outline = RuleDark,
    outlineVariant = RuleDark,
    inverseSurface = InkDark,
    inverseOnSurface = PaperDark
)

// 形状:手册 = 直角。除输入框圆 2dp 外,卡片、chips、按钮一律直角,强化账本感。
val LedgerShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
)

@Composable
fun GrowthOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GrowthOSTypography,
        shapes = LedgerShapes,
        content = content
    )
}
