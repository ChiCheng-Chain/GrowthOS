package com.growthos.app.ui.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题色板单测(feature 2026-08-28 主题切换 / 设计「验证设计」)。
 *
 * 核心护栏:石灰纸(默认)light/dark 逐槽断言 hex——锁定历史外观零变化(BR-2),
 * 防「顺手优化配色」。另验全部 preset 映射完备与 name 解析回退。
 */
class PaletteTest {

    // ---------- AC-03:石灰纸逐槽锁定(与旧 Theme.kt 常量逐字一致) ----------

    @Test
    fun `limestone light slots locked to legacy values`() {
        val c = GrowthThemePreset.Limestone.palette.light
        assertEquals(0xFFF4F1EA, c.paper.toArgbHex())
        assertEquals(0xFFECE7DB, c.paperDim.toArgbHex())
        assertEquals(0xFF1A1814, c.ink.toArgbHex())
        assertEquals(0xFF4A463E, c.inkSoft.toArgbHex())
        assertEquals(0xFF8B8578, c.inkFaint.toArgbHex())
        assertEquals(0xFFB5651D, c.accent.toArgbHex())
        assertEquals(0xFF5A3A0E, c.accentDim.toArgbHex())
        assertEquals(0xFFE8D4B8, c.accentSoft.toArgbHex())
        assertEquals(0xFFD9D2C2, c.rule.toArgbHex())
        assertEquals(0xFFB3261E, c.error.toArgbHex()) // M3 默认红(BR-2)
    }

    @Test
    fun `limestone dark slots locked to legacy values`() {
        val c = GrowthThemePreset.Limestone.palette.dark
        assertEquals(0xFF13110D, c.paper.toArgbHex())
        assertEquals(0xFF1C1914, c.paperDim.toArgbHex())
        assertEquals(0xFFE8E3D6, c.ink.toArgbHex())
        assertEquals(0xFFB8B0A0, c.inkSoft.toArgbHex())
        assertEquals(0xFFB5651D, c.accent.toArgbHex())
        assertEquals(0xFF5A3A0E, c.accentDim.toArgbHex()) // 原 primaryContainer
        assertEquals(0xFFE8D4B8, c.accentSoft.toArgbHex())
        assertEquals(0xFF2E2A22, c.rule.toArgbHex())
        assertEquals(0xFFF2B8B5, c.error.toArgbHex()) // M3 默认 dark 红
    }

    @Test
    fun `limestone color scheme maps legacy slots`() {
        val light = GrowthThemePreset.Limestone.palette.light.toColorScheme(dark = false)
        // 抽验映射:primary/secondary/background/surfaceVariant/outline/inverse
        assertEquals(0xFFB5651D, light.primary.toArgbHex())
        assertEquals(0xFF4A463E, light.secondary.toArgbHex())
        assertEquals(0xFFF4F1EA, light.background.toArgbHex())
        assertEquals(0xFFECE7DB, light.surfaceVariant.toArgbHex())
        assertEquals(0xFFD9D2C2, light.outline.toArgbHex())
        assertEquals(0xFF1A1814, light.inverseSurface.toArgbHex())
        // onPrimary 语义:light 用白
        assertEquals(0xFFFFFFFF, light.onPrimary.toArgbHex())

        val dark = GrowthThemePreset.Limestone.palette.dark.toColorScheme(dark = true)
        assertEquals(0xFFB5651D, dark.primary.toArgbHex())
        assertEquals(0xFF5A3A0E, dark.primaryContainer.toArgbHex())
        assertEquals(0xFF13110D, dark.background.toArgbHex())
        // onPrimary 语义:dark 用纸色
        assertEquals(0xFF13110D, dark.onPrimary.toArgbHex())
    }

    // ---------- 槽位完备:全部 preset 映射后无 Unspecified ----------

    @Test
    fun `all presets map every scheme slot without unspecified`() {
        GrowthThemePreset.entries.forEach { preset ->
            listOf(false, true).forEach { dark ->
                val scheme = (if (dark) preset.palette.dark else preset.palette.light)
                    .toColorScheme(dark = dark)
                listOf(
                    "primary" to scheme.primary,
                    "onPrimary" to scheme.onPrimary,
                    "primaryContainer" to scheme.primaryContainer,
                    "onPrimaryContainer" to scheme.onPrimaryContainer,
                    "secondary" to scheme.secondary,
                    "onSecondary" to scheme.onSecondary,
                    "secondaryContainer" to scheme.secondaryContainer,
                    "onSecondaryContainer" to scheme.onSecondaryContainer,
                    "tertiary" to scheme.tertiary,
                    "onTertiary" to scheme.onTertiary,
                    "background" to scheme.background,
                    "onBackground" to scheme.onBackground,
                    "surface" to scheme.surface,
                    "onSurface" to scheme.onSurface,
                    "surfaceVariant" to scheme.surfaceVariant,
                    "onSurfaceVariant" to scheme.onSurfaceVariant,
                    "outline" to scheme.outline,
                    "inverseSurface" to scheme.inverseSurface
                ).forEach { (slot, color) ->
                    assertTrue(
                        "${preset.name} ${if (dark) "dark" else "light"} $slot 不应为 Unspecified",
                        color != androidx.compose.ui.graphics.Color.Unspecified
                    )
                }
            }
        }
    }

    // ---------- preset 语义 ----------

    @Test
    fun `five presets exist with distinct palettes`() {
        assertEquals(5, GrowthThemePreset.entries.size)
        // 各套纸色互不相同(主题身份可分辨)
        val papers = GrowthThemePreset.entries.map { it.palette.light.paper }
        assertEquals(papers.size, papers.toSet().size)
        val accents = GrowthThemePreset.entries.map { it.palette.light.accent }
        assertEquals(accents.size, accents.toSet().size)
    }

    @Test
    fun `fromName returns preset by name and falls back on unknown`() {
        assertSame(GrowthThemePreset.Blueprint, GrowthThemePreset.fromName("Blueprint"))
        assertSame(GrowthThemePreset.DEFAULT, GrowthThemePreset.fromName(null))
        assertSame(GrowthThemePreset.DEFAULT, GrowthThemePreset.fromName("不存在的主题"))
    }

    @Test
    fun `default is limestone`() {
        assertSame(GrowthThemePreset.Limestone, GrowthThemePreset.DEFAULT)
        assertNotNull(GrowthThemePreset.DEFAULT.label)
    }

    /** Compose Color 在 JVM 单测(非 Android 运行时)下 value 为压缩位段,须用 toArgb() 还原。 */
    private fun androidx.compose.ui.graphics.Color.toArgbHex(): Long =
        toArgb().toLong() and 0xFFFFFFFFL
}
