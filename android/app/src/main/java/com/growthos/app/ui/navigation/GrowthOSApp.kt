package com.growthos.app.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.growthos.app.ui.domain_view.DomainScreen
import com.growthos.app.ui.record.RecordScreen
import com.growthos.app.ui.record.SampleEditScreen
import com.growthos.app.ui.error_type.ErrorTypeListScreen
import com.growthos.app.ui.knowledge.KnowledgeEditScreen
import com.growthos.app.ui.knowledge.KnowledgeListScreen
import com.growthos.app.ui.principle.PrincipleEditScreen
import com.growthos.app.ui.principle.PrincipleListScreen
import com.growthos.app.ui.settings.SettingsScreen
import com.growthos.app.ui.theme.GrowthOSTheme
import com.growthos.app.ui.theme.MonoFamily
import com.growthos.app.ui.training.TrainingEditScreen
import com.growthos.app.ui.training.TrainingEffectScreen
import com.growthos.app.ui.training.TrainingListScreen
import com.growthos.app.ui.weekly.WeeklyScreen
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

// 三个底部 Tab 入口
private sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Record : Tab("record", "记录", Icons.Outlined.AddCircle)
    data object Domains : Tab("domains", "领域", Icons.Outlined.ViewList)
    data object Weekly : Tab("weekly", "复盘", Icons.Outlined.Insights)
}

private val tabs = listOf(Tab.Record, Tab.Domains, Tab.Weekly)

// 子页面路由(技术方案 §4.1)
private object Routes {
    const val SAMPLE_EDIT = "sample_edit"
    const val SAMPLE_EDIT_WITH_ID = "sample_edit?sampleId={sampleId}"
    fun sampleEdit(sampleId: Long? = null): String =
        if (sampleId != null && sampleId > 0) "sample_edit?sampleId=$sampleId"
        else "sample_edit"

    // 阶段 5 训练项
    const val TRAINING_LIST = "training_list"
    const val TRAINING_EDIT = "training_edit"
    const val TRAINING_EDIT_WITH_ERROR_TYPE = "training_edit?errorTypeId={errorTypeId}"
    fun trainingEdit(errorTypeId: Long? = null): String =
        if (errorTypeId != null && errorTypeId > 0) "training_edit?errorTypeId=$errorTypeId"
        else "training_edit"
    const val TRAINING_EFFECT_WITH_ID = "training_effect?trainingId={trainingId}"
    fun trainingEffect(trainingId: Long): String = "training_effect?trainingId=$trainingId"

    // 阶段 6 原则
    const val PRINCIPLE_LIST = "principle_list"
    const val PRINCIPLE_EDIT = "principle_edit"
    const val PRINCIPLE_EDIT_WITH_ID = "principle_edit?principleId={principleId}&trainingId={trainingId}&sampleId={sampleId}"
    fun principleEdit(principleId: Long? = null, trainingId: Long? = null, sampleId: Long? = null): String {
        val pid = principleId?.takeIf { it > 0 } ?: -1L
        val tid = trainingId?.takeIf { it > 0 } ?: -1L
        val sid = sampleId?.takeIf { it > 0 } ?: -1L
        return "principle_edit?principleId=$pid&trainingId=$tid&sampleId=$sid"
    }

    // 阶段 7 设置
    const val SETTINGS = "settings"

    // CRUD 补全:错误类型管理页
    const val ERROR_TYPES = "error_types"

    // 知识库
    const val KNOWLEDGE_LIST = "knowledge_list"
    const val KNOWLEDGE_EDIT = "knowledge_edit"
    const val KNOWLEDGE_EDIT_WITH_ID = "knowledge_edit?knowledgeId={knowledgeId}"
    fun knowledgeEdit(knowledgeId: Long? = null): String {
        val kid = knowledgeId?.takeIf { it > 0 } ?: -1L
        return "knowledge_edit?knowledgeId=$kid"
    }
}

@Composable
fun GrowthOSApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            LedgerBottomBar(currentRoute = currentRoute) { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    ) { innerPadding ->
        // 统一淡入淡出转场:消除默认 slide+fade 带来的横向位移"晃眼"感,
        // Tab 切换与子页跳转都走纯 alpha 过渡,时长与缓动贴近 M3 推荐值。
        val durationMs = 220
        val fadeSpec = tween<Float>(durationMs, easing = FastOutSlowInEasing)
        NavHost(
            navController = navController,
            startDestination = Tab.Record.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(fadeSpec) },
            exitTransition = { fadeOut(fadeSpec) },
            popEnterTransition = { fadeIn(fadeSpec) },
            popExitTransition = { fadeOut(fadeSpec) }
        ) {
            composable(Tab.Record.route) {
                RecordScreen(
                    onNavigateToEdit = { sampleId ->
                        navController.navigate(Routes.sampleEdit(sampleId))
                    }
                )
            }
            composable(Tab.Domains.route) {
                DomainScreen(
                    onOpenSample = { sampleId ->
                        navController.navigate(Routes.sampleEdit(sampleId))
                    },
                    onNavigateToEffect = { trainingId ->
                        navController.navigate(Routes.trainingEffect(trainingId))
                    },
                    onNavigateToPrincipleEdit = { principleId ->
                        navController.navigate(Routes.principleEdit(principleId = principleId))
                    },
                    onNavigateToKnowledgeEdit = { knowledgeId ->
                        navController.navigate(Routes.knowledgeEdit(knowledgeId))
                    }
                )
            }
            composable(Tab.Weekly.route) {
                WeeklyScreen(
                    onNavigateToCreateTraining = { errorTypeId ->
                        navController.navigate(Routes.trainingEdit(errorTypeId))
                    },
                    onNavigateToTrainingList = {
                        navController.navigate(Routes.TRAINING_LIST)
                    },
                    onNavigateToPrincipleList = {
                        navController.navigate(Routes.PRINCIPLE_LIST)
                    },
                    onNavigateToErrorTypes = {
                        navController.navigate(Routes.ERROR_TYPES)
                    },
                    onNavigateToKnowledge = {
                        navController.navigate(Routes.KNOWLEDGE_LIST)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Routes.SETTINGS)
                    }
                )
            }
            composable(
                route = Routes.SAMPLE_EDIT_WITH_ID,
                arguments = listOf(
                    navArgument("sampleId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val sampleId = backStackEntry.arguments?.getLong("sampleId") ?: -1L
                SampleEditScreen(
                    sampleId = sampleId.takeIf { it > 0 },
                    onBack = { navController.popBackStack() },
                    onNavigateToDomains = {
                        navController.navigate(Tab.Domains.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Routes.TRAINING_LIST) {
                TrainingListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEffect = { trainingId ->
                        navController.navigate(Routes.trainingEffect(trainingId))
                    },
                    onOpenCreate = {
                        navController.navigate(Routes.trainingEdit())
                    }
                )
            }
            composable(
                route = Routes.TRAINING_EDIT_WITH_ERROR_TYPE,
                arguments = listOf(
                    navArgument("errorTypeId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val errorTypeId = backStackEntry.arguments?.getLong("errorTypeId") ?: -1L
                TrainingEditScreen(
                    prefillErrorTypeId = errorTypeId.takeIf { it > 0 },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.TRAINING_EFFECT_WITH_ID,
                arguments = listOf(
                    navArgument("trainingId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val trainingId = backStackEntry.arguments?.getLong("trainingId") ?: -1L
                TrainingEffectScreen(
                    trainingId = trainingId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.PRINCIPLE_LIST) {
                PrincipleListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEdit = { principleId ->
                        navController.navigate(Routes.principleEdit(principleId = principleId))
                    },
                    onOpenCreate = {
                        navController.navigate(Routes.principleEdit())
                    }
                )
            }
            composable(
                route = Routes.PRINCIPLE_EDIT_WITH_ID,
                arguments = listOf(
                    navArgument("principleId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("trainingId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("sampleId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val principleId = backStackEntry.arguments?.getLong("principleId") ?: -1L
                val trainingId = backStackEntry.arguments?.getLong("trainingId") ?: -1L
                val sampleId = backStackEntry.arguments?.getLong("sampleId") ?: -1L
                PrincipleEditScreen(
                    principleId = principleId.takeIf { it > 0 },
                    prefillTrainingId = trainingId.takeIf { it > 0 },
                    prefillSampleId = sampleId.takeIf { it > 0 },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.ERROR_TYPES) {
                ErrorTypeListScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.KNOWLEDGE_LIST) {
                KnowledgeListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEdit = { knowledgeId ->
                        navController.navigate(Routes.knowledgeEdit(knowledgeId))
                    },
                    onOpenCreate = {
                        navController.navigate(Routes.knowledgeEdit())
                    }
                )
            }
            composable(
                route = Routes.KNOWLEDGE_EDIT_WITH_ID,
                arguments = listOf(
                    navArgument("knowledgeId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val knowledgeId = backStackEntry.arguments?.getLong("knowledgeId") ?: -1L
                KnowledgeEditScreen(
                    knowledgeId = knowledgeId.takeIf { it > 0 },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun LedgerBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit
) {
    // 顶部一根发丝线,把导航和内容分隔开;无背景色、无阴影,贴底。
    Column {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(tab.route) },
                    icon = {
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.height(22.dp)
                        )
                    },
                    label = {
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = MonoFamily,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}

// ---------- Previews ----------

@Preview(name = "GrowthOS Root", showBackground = true, heightDp = 800)
@Composable
private fun GrowthOSRootPreview() {
    GrowthOSTheme {
        GrowthOSApp()
    }
}

@Preview(name = "今日记录", showBackground = true, heightDp = 900)
@Composable
private fun RecordScreenPreview() {
    GrowthOSTheme { RecordScreen(onNavigateToEdit = {}) }
}

@Preview(name = "周复盘", showBackground = true, heightDp = 1000)
@Composable
private fun WeeklyScreenPreview() {
    GrowthOSTheme { WeeklyScreen() }
}
