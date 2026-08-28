package com.growthos.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.growthos.app.ui.theme.GrowthThemePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 主题偏好持久化(feature 2026-08-28 主题切换)。
 *
 * 镜像 [SelectedDomainStore] 范式:UI 偏好存 DataStore Preferences 而非 Room;
 * interface 便于 ViewModel 单测注桩。由 [com.growthos.app.di.AppContainer] 持有单例
 * (DataStore 进程级唯一)。
 *
 * 持久化枚举 name(防重排漂移),未知值/无值回退 [GrowthThemePreset.DEFAULT](BR-5)。
 */
interface ThemeStore {
    /** 当前持久化的主题;null 表示未选过(应按默认渲染)。 */
    val flow: Flow<GrowthThemePreset?>

    /** 写入主题选择。 */
    suspend fun set(preset: GrowthThemePreset)
}

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "theme_prefs"
)

class ThemeStoreImpl(private val context: Context) : ThemeStore {

    private val key = stringPreferencesKey("theme_preset")

    override val flow: Flow<GrowthThemePreset?> =
        context.themeDataStore.data.map { prefs -> prefs[key]?.let { GrowthThemePreset.fromName(it) } }

    override suspend fun set(preset: GrowthThemePreset) {
        context.themeDataStore.edit { prefs ->
            prefs[key] = preset.name
        }
    }
}
