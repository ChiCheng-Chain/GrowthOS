package com.growthos.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 主题色板(feature 2026-08-28 主题切换 / 设计 D1)。
 *
 * 每套主题 = [LedgerPalette](light+dark 各 [LedgerSchemeColors] 10 槽)+ 一处质感签名。
 * 语义沿用复盘手册约定:accent 只用于「向前看/可控行动/选中态」;
 * error 只用于删除类;rule 是账本发丝线。
 *
 * 石灰纸(Limestone)= 历史默认配色逐槽照抄(含 error=Material 默认红、
 * dark accentDim=#5A3A0E 原 primaryContainer),PaletteTest 逐槽锁定,防顺手漂移。
 */

data class LedgerSchemeColors(
    val paper: Color,        // background/surface
    val paperDim: Color,     // surfaceVariant/secondaryContainer
    val ink: Color,          // onBackground/onSurface/inverseSurface
    val inkSoft: Color,      // onSurfaceVariant/secondary
    val inkFaint: Color,     // 预留最弱灰阶
    val accent: Color,       // primary/tertiary/surfaceTint
    val accentDim: Color,    // dark primaryContainer
    val accentSoft: Color,   // light primaryContainer
    val rule: Color,         // outline/outlineVariant
    val error: Color
)

data class LedgerPalette(
    val light: LedgerSchemeColors,
    val dark: LedgerSchemeColors
)

/** 槽位映射:10 色板 → M3 ColorScheme 18 槽(复刻原 Theme.kt 赋值逻辑)。 */
internal fun LedgerSchemeColors.toColorScheme(dark: Boolean): ColorScheme {
    val onAccent = if (dark) paper else Color.White
    return if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentDim,
            onPrimaryContainer = accentSoft,
            secondary = inkSoft,
            onSecondary = paper,
            secondaryContainer = paperDim,
            onSecondaryContainer = ink,
            tertiary = accent,
            onTertiary = onAccent,
            background = paper,
            onBackground = ink,
            surface = paper,
            onSurface = ink,
            surfaceVariant = paperDim,
            onSurfaceVariant = inkSoft,
            surfaceTint = accent,
            outline = rule,
            outlineVariant = rule,
            inverseSurface = ink,
            inverseOnSurface = paper
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentSoft,
            onPrimaryContainer = ink,
            secondary = inkSoft,
            onSecondary = paper,
            secondaryContainer = paperDim,
            onSecondaryContainer = ink,
            tertiary = accent,
            onTertiary = onAccent,
            background = paper,
            onBackground = ink,
            surface = paper,
            onSurface = ink,
            surfaceVariant = paperDim,
            onSurfaceVariant = inkSoft,
            surfaceTint = accent,
            outline = rule,
            outlineVariant = rule,
            inverseSurface = ink,
            inverseOnSurface = paper
        )
    }
}

/**
 * 预设主题(BR-3)。name 即持久化键(BR-5),不可重命名;
 * 新增主题只能追加,不可插入中间(防既有用户偏好漂移)。
 */
enum class GrowthThemePreset(
    val label: String,
    val palette: LedgerPalette
) {
    /** 石灰纸(默认):历史配色原样,签名=零变化。 */
    Limestone(
        label = "石灰纸",
        palette = LedgerPalette(
            light = LedgerSchemeColors(
                paper = Color(0xFFF4F1EA),
                paperDim = Color(0xFFECE7DB),
                ink = Color(0xFF1A1814),
                inkSoft = Color(0xFF4A463E),
                inkFaint = Color(0xFF8B8578),
                accent = Color(0xFFB5651D),
                accentDim = Color(0xFF5A3A0E),
                accentSoft = Color(0xFFE8D4B8),
                rule = Color(0xFFD9D2C2),
                error = Color(0xFFB3261E) // M3 默认红(BR-2 锁定)
            ),
            dark = LedgerSchemeColors(
                paper = Color(0xFF13110D),
                paperDim = Color(0xFF1C1914),
                ink = Color(0xFFE8E3D6),
                inkSoft = Color(0xFFB8B0A0),
                inkFaint = Color(0xFF8B8578),
                accent = Color(0xFFB5651D),
                accentDim = Color(0xFF5A3A0E),
                accentSoft = Color(0xFFE8D4B8),
                rule = Color(0xFF2E2A22),
                error = Color(0xFFF2B8B5) // M3 默认 dark 红(BR-2 锁定)
            )
        )
    ),

    /**
     * 蓝晒图:工程蓝图气质。冷白纸+蓝黑墨+普鲁士蓝。
     * 签名:LedgerRule 点线(工程图标注线,见 dashed 参数)。
     */
    Blueprint(
        label = "蓝晒图",
        palette = LedgerPalette(
            light = LedgerSchemeColors(
                paper = Color(0xFFEDF1F5),
                paperDim = Color(0xFFE2E8EF),
                ink = Color(0xFF16202B),
                inkSoft = Color(0xFF3E4C5C),
                inkFaint = Color(0xFF7B8898),
                accent = Color(0xFF1F4E79),
                accentDim = Color(0xFF173A5C),
                accentSoft = Color(0xFFC9D9EA),
                rule = Color(0xFFC3CEDB),
                error = Color(0xFFB3261E)
            ),
            dark = LedgerSchemeColors(
                paper = Color(0xFF0E1319),
                paperDim = Color(0xFF161D26),
                ink = Color(0xFFDCE3EA),
                inkSoft = Color(0xFFA5B1BE),
                inkFaint = Color(0xFF6E7A87),
                accent = Color(0xFF6B9FD4),
                accentDim = Color(0xFF1E3A55),
                accentSoft = Color(0xFFC9D9EA),
                rule = Color(0xFF242D38),
                error = Color(0xFFF2B8B5)
            )
        )
    ),

    /**
     * 松烟墨:文房水墨气质。灰白宣纸+碳黑+石绿。
     * 签名:灰阶更柔(paperDim 贴近 paper,层次过渡缓)。
     */
    PineSmoke(
        label = "松烟墨",
        palette = LedgerPalette(
            light = LedgerSchemeColors(
                paper = Color(0xFFF0EEE9),
                paperDim = Color(0xFFE9E7E1),
                ink = Color(0xFF1B1D1C),
                inkSoft = Color(0xFF454946),
                inkFaint = Color(0xFF838883),
                accent = Color(0xFF2E7D6B),
                accentDim = Color(0xFF1E5246),
                accentSoft = Color(0xFFC8E2DA),
                rule = Color(0xFFD6D4CD),
                error = Color(0xFFB3261E)
            ),
            dark = LedgerSchemeColors(
                paper = Color(0xFF101312),
                paperDim = Color(0xFF171B1A),
                ink = Color(0xFFDFE3E0),
                inkSoft = Color(0xFFA8AFAB),
                inkFaint = Color(0xFF6E7470),
                accent = Color(0xFF5FA894),
                accentDim = Color(0xFF1C443A),
                accentSoft = Color(0xFFC8E2DA),
                rule = Color(0xFF232726),
                error = Color(0xFFF2B8B5)
            )
        )
    ),

    /**
     * 朱丝栏:古籍信笺气质。米白笺纸+墨黑+朱砂。
     * 签名:发丝线(rule 槽)为朱红,账本线本身带色。
     */
    Vermilion(
        label = "朱丝栏",
        palette = LedgerPalette(
            light = LedgerSchemeColors(
                paper = Color(0xFFF5F0E4),
                paperDim = Color(0xFFEEE7D6),
                ink = Color(0xFF1F1B16),
                inkSoft = Color(0xFF4E483E),
                inkFaint = Color(0xFF8D867A),
                accent = Color(0xFFC0392B),
                accentDim = Color(0xFF8C2A20),
                accentSoft = Color(0xFFF0D5CE),
                rule = Color(0xFFD9B8AC),
                error = Color(0xFFB3261E)
            ),
            dark = LedgerSchemeColors(
                paper = Color(0xFF14100C),
                paperDim = Color(0xFF1D1812),
                ink = Color(0xFFEAE2D3),
                inkSoft = Color(0xFFB3A895),
                inkFaint = Color(0xFF7A7264),
                accent = Color(0xFFD9604F),
                accentDim = Color(0xFF5C261E),
                accentSoft = Color(0xFFF0D5CE),
                rule = Color(0xFF332822),
                error = Color(0xFFF2B8B5)
            )
        )
    ),

    /**
     * 打字机:机械报表气质。米白纸+碳黑+铭黄(深墨 onAccent 反转)。
     * 签名:primaryContainer(accentSoft)大面积用——标签底色反转强调。
     */
    Typewriter(
        label = "打字机",
        palette = LedgerPalette(
            light = LedgerSchemeColors(
                paper = Color(0xFFF3F0E8),
                paperDim = Color(0xFFEAE6DA),
                ink = Color(0xFF211E19),
                inkSoft = Color(0xFF4B4740),
                inkFaint = Color(0xFF88837A),
                accent = Color(0xFF9A7318),
                accentDim = Color(0xFF6B5010),
                accentSoft = Color(0xFFF2E3BD),
                rule = Color(0xFFD8D3C5),
                error = Color(0xFFB3261E)
            ),
            dark = LedgerSchemeColors(
                paper = Color(0xFF121110),
                paperDim = Color(0xFF1A1917),
                ink = Color(0xFFE7E4DC),
                inkSoft = Color(0xFFAEAA9F),
                inkFaint = Color(0xFF716D63),
                accent = Color(0xFFD9A441),
                accentDim = Color(0xFF574312),
                accentSoft = Color(0xFFF2E3BD),
                rule = Color(0xFF26251F),
                error = Color(0xFFF2B8B5)
            )
        )
    );

    companion object {
        val DEFAULT = Limestone

        /** 持久化 name → 枚举;未知/null 回退默认(BR-5)。 */
        fun fromName(name: String?): GrowthThemePreset =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
