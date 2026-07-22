package com.growthos.app.ui.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.growthos.app.GrowthOSApp
import com.growthos.app.ui.components.Eyebrow
import com.growthos.app.ui.components.LedgerRule
import com.growthos.app.ui.theme.GrowthOSTheme
import com.growthos.app.ui.theme.MonoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设置页(阶段 7 / 需求 F1)。
 *
 * 周复盘页顶部入口进入。本阶段只含「导出数据」(R-013),为后续关于 / 主题扩展预留。
 * 导出走 SAF CreateDocument:点「导出数据」→ vm 生成 JSON → emit Ready →
 * 启动系统文件保存选择器 → 用户选位置命名 → 写入 Uri → 提示结果。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    onBack: () -> Unit
) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container.dataExporter))
    val state by vm.uiState.collectAsStateWithLifecycle()
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

    // 终态提示(Success / Failed)→ Snackbar,提示后复位。
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

    SettingsContent(
        state = state,
        onBack = onBack,
        onExport = vm::export,
        snackbarHostState = snackbarHostState
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    onExport: () -> Unit,
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
                "数据",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            val exporting = state.exportState is ExportState.Exporting ||
                state.exportState is ExportState.Ready
            ExportRow(
                title = "导出数据",
                subtitle = "导出全部领域 / 样本 / 错误类型 / 训练项 / 原则为 JSON",
                enabled = !exporting,
                onClick = onExport
            )
            LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ExportRow(
    title: String,
    subtitle: String,
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
                Icons.Outlined.FileDownload,
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
                    "导出中",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = MonoFamily
                )
            }
        }
    }
}

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

// ---------- Previews ----------

@Preview(name = "设置(空闲)", showBackground = true, heightDp = 600)
@Composable
private fun SettingsIdlePreview() {
    GrowthOSTheme {
        SettingsContent(
            state = SettingsUiState(ExportState.Idle),
            onBack = {}, onExport = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}

@Preview(name = "设置(导出中)", showBackground = true, heightDp = 600)
@Composable
private fun SettingsExportingPreview() {
    GrowthOSTheme {
        SettingsContent(
            state = SettingsUiState(ExportState.Exporting),
            onBack = {}, onExport = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}
