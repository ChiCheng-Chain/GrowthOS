package com.growthos.app.ui.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.growthos.app.GrowthOSApp
import com.growthos.app.data.export.ImportCounts
import com.growthos.app.data.export.ImportPreview
import com.growthos.app.data.export.TableCounts
import com.growthos.app.ui.components.Eyebrow
import com.growthos.app.ui.components.LedgerRule
import com.growthos.app.ui.theme.GrowthOSTheme
import com.growthos.app.ui.theme.GrowthThemePreset
import com.growthos.app.ui.theme.MonoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设置页(阶段 7 / 需求 F1;导入 feature 2026-08-27 扩展)。
 *
 * 周复盘页顶部入口进入。「数据」分组含导出/导入两行(BR-8)。
 * 导出走 SAF CreateDocument:点「导出数据」→ vm 生成 JSON → emit Ready →
 * 启动系统文件保存选择器 → 用户选位置命名 → 写入 Uri → 提示结果。
 * 导入走 SAF OpenDocument:点「导入数据」→ 选 .json 文件 → 读文本喂 vm.import →
 * Parsing → Confirming 弹双向对照确认框(BR-7)→ 确认 → Importing → 成功/失败提示。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    onBack: () -> Unit
) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container.dataExporter, container.dataImporter, container.themeStore))
    val state by vm.uiState.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 暂存 Ready 携带的 json:launcher 回调拿不到入参,需在启动时捕获。
    // 用 remember 而非顶层 var,避免多实例串扰与配置变更丢失。
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var launcherArmed by remember { mutableStateOf(false) }

    // SAF 文件保存选择器:用户选位置 + 命名 → 回调 Uri。
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingJson
        pendingJson = null
        launcherArmed = false
        if (uri == null) {
            vm.onFailed("导出未完成")
        } else {
            val ok = writeJsonToUri(context, uri, json)
            if (ok) vm.onWritten() else vm.onFailed("写入失败")
        }
    }

    // SAF 文件选择器(BR-10):选既有 .json 备份 → 读文本喂 vm.import。
    // 取消选择器 = 无操作(uri == null 静默,不进状态机)。
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = readTextFromUri(context, uri)
        if (text == null) {
            vm.import("")
        } else {
            vm.import(text)
        }
    }

    // 检测 Ready:捕获 json,启动 launcher。必须在 LaunchedEffect 中调,不能在组合期直接调。
    // 用 launcherArmed 防重组时重复触发。
    LaunchedEffect(state.exportState) {
        if (state.exportState is ExportState.Ready && !launcherArmed) {
            pendingJson = (state.exportState as ExportState.Ready).json
            launcherArmed = true
            vm.onConsumed()
            createDocumentLauncher.launch(defaultFileName())
        }
    }

    // 导入终态提示(Success / Failed)→ Snackbar,提示后复位(BR-6/BR-9)。
    LaunchedEffect(state.importState) {
        when (val s = state.importState) {
            is ImportState.Success -> {
                snackbarHostState.showSnackbar(importSuccessMessage(s.counts))
                vm.reset()
            }
            is ImportState.Failed -> {
                snackbarHostState.showSnackbar(s.reason)
                vm.reset()
            }
            else -> {}
        }
    }

    // 导出终态提示(Success / Failed)→ Snackbar,提示后复位。
    LaunchedEffect(state.exportState) {
        when (val s = state.exportState) {
            is ExportState.Success -> {
                snackbarHostState.showSnackbar("已导出")
                vm.reset()
            }
            is ExportState.Failed -> {
                snackbarHostState.showSnackbar(s.reason)
                vm.reset()
            }
            else -> {}
        }
    }

    // 确认框(BR-2/BR-7):Confirming 时弹出,双向对照当前库与备份文件。
    val confirming = state.importState as? ImportState.Confirming
    if (confirming != null) {
        ImportConfirmDialog(
            preview = confirming.preview,
            onConfirm = vm::onImportConfirmed,
            onDismiss = vm::onImportCancelled
        )
    }

    SettingsContent(
        state = state,
        selectedTheme = theme,
        onThemeSelect = vm::setTheme,
        onBack = onBack,
        onExport = vm::export,
        onImport = { openDocumentLauncher.launch(arrayOf("application/json")) },
        snackbarHostState = snackbarHostState
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SettingsContent(
    state: SettingsUiState,
    selectedTheme: GrowthThemePreset,
    onThemeSelect: (GrowthThemePreset) -> Unit,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
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
        ) {
            Eyebrow(
                "外观",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            ThemePresetRow(
                selected = selectedTheme,
                onSelect = onThemeSelect
            )
            LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))

            Eyebrow(
                "数据",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            // 互斥派生(设计 D6 / BR-8):导出中(含 Ready)禁导入;导入中(含解析/确认框)禁导出
            val exporting = state.exportState is ExportState.Exporting ||
                state.exportState is ExportState.Ready
            val importing = state.importState is ImportState.Parsing ||
                state.importState is ImportState.Confirming ||
                state.importState is ImportState.Importing

            DataActionRow(
                title = "导出数据",
                subtitle = "导出全部领域 / 样本 / 错误类型 / 训练项 / 原则为 JSON",
                icon = Icons.Outlined.FileDownload,
                busy = exporting,
                busyLabel = "导出中",
                enabled = !exporting && !importing,
                onClick = onExport
            )
            DataActionRow(
                title = "导入数据",
                subtitle = "从导出的 JSON 备份恢复全部数据(替换现有)",
                icon = Icons.Outlined.FileUpload,
                busy = importing,
                busyLabel = "导入中",
                enabled = !exporting && !importing,
                onClick = onImport
            )
            LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * 主题样张行(BR-8/BR-9):横向滚动的预设卡。
 * 色样取各 preset.palette.light 原始值(未选中主题也显示自己的纸色),
 * 选中态描边用当前 scheme 的 primary。点选立即全局生效(经 ThemeStore 回声)。
 */
@Composable
private fun ThemePresetRow(
    selected: GrowthThemePreset,
    onSelect: (GrowthThemePreset) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GrowthThemePreset.entries.forEach { preset ->
            ThemeSwatchCard(
                preset = preset,
                selected = preset == selected,
                onClick = { onSelect(preset) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 单张主题样张:纸色块 + accent 细竖线 + 名称,直角无阴影(账本范式)。 */
@Composable
private fun ThemeSwatchCard(
    preset: GrowthThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = preset.palette.light
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(0.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        // 纸色样张:accent 左竖线 + 纸底 + 底部 rule 发丝线,小账本卡缩影
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .drawBehind {
                    val w = 3.dp.toPx()
                    drawLine(
                        color = c.accent,
                        start = Offset(w / 2, 0f),
                        end = Offset(w / 2, size.height),
                        strokeWidth = w
                    )
                    drawLine(
                        color = c.rule,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .background(c.paper)
                .padding(start = 10.dp, top = 6.dp)
        ) {
            // 两粒墨点样张:ink 强弱层次
            Box(
                Modifier
                    .width(28.dp)
                    .height(3.dp)
                    .background(c.ink)
            )
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .width(20.dp)
                    .height(3.dp)
                    .background(c.inkSoft)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = preset.label + if (selected) " ✓" else "",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp)
        )
    }
}

/** 数据分组通用行(原 ExportRow 泛化:导入行复用同一形态,BR-8)。 */
@Composable
private fun DataActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    busy: Boolean,
    busyLabel: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = { if (enabled) onClick() },
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (enabled) title else "$title…",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!enabled) {
                Text(
                    busyLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = MonoFamily
                )
            }
        }
    }
}

/** 导入确认框(BR-2/BR-7):双向对照 + 明示替换不可逆。 */
@Composable
private fun ImportConfirmDialog(
    preview: ImportPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入备份", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "导入将替换本机当前全部数据,此操作不可撤销。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "当前本机:${countsLine(preview.currentCounts)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "备份文件:${countsLine(preview.backupCounts)}(v${preview.version},导出于 ${formatExportedAt(preview.exportedAt)})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("替换现有数据", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 「N 样本 / M 领域 / K 训练项…」计数行(BR-6/BR-7 共用)。 */
private fun countsLine(c: TableCounts): String =
    "${c.samples} 样本 / ${c.domains} 领域 / ${c.trainings} 训练项 / ${c.principles} 原则 / ${c.knowledges} 知识"

/** 成功提示:「已导入:N 样本 / M 领域…」(BR-6)。 */
private fun importSuccessMessage(counts: ImportCounts): String =
    "已导入:${countsLine(counts.tableCounts)}"

private fun formatExportedAt(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

/** 默认文件名:growthos-export-yyyyMMdd-HHmmss.json。 */
private fun defaultFileName(): String {
    val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
    return "growthos-export-$ts.json"
}

/** 把 JSON 写入用户选定的 Uri(走 contentResolver,无需存储权限)。 */
private fun writeJsonToUri(context: Context, uri: android.net.Uri, json: String?): Boolean {
    if (json == null) return false
    return try {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        } != null
    } catch (t: Throwable) {
        false
    }
}

/** 从用户选定的 Uri 读全部文本(导入路径)。读不出返回 null。 */
private fun readTextFromUri(context: Context, uri: android.net.Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { input ->
        input.readBytes().toString(Charsets.UTF_8)
    }
} catch (t: Throwable) {
    null
}

// ---------- Previews ----------

@Preview(name = "设置(空闲)", showBackground = true, heightDp = 600)
@Composable
private fun SettingsIdlePreview() {
    GrowthOSTheme {
        SettingsContent(
            state = SettingsUiState(),
            selectedTheme = GrowthThemePreset.DEFAULT, onThemeSelect = {}, onBack = {}, onExport = {}, onImport = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}

@Preview(name = "设置(导出中)", showBackground = true, heightDp = 600)
@Composable
private fun SettingsExportingPreview() {
    GrowthOSTheme {
        SettingsContent(
            state = SettingsUiState(exportState = ExportState.Exporting),
            selectedTheme = GrowthThemePreset.DEFAULT, onThemeSelect = {}, onBack = {}, onExport = {}, onImport = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}

@Preview(name = "设置(导入中)", showBackground = true, heightDp = 600)
@Composable
private fun SettingsImportingPreview() {
    GrowthOSTheme {
        SettingsContent(
            state = SettingsUiState(importState = ImportState.Importing),
            selectedTheme = GrowthThemePreset.DEFAULT, onThemeSelect = {}, onBack = {}, onExport = {}, onImport = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}
