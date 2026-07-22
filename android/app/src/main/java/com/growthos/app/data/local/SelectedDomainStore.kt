package com.growthos.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 当前选中领域的持久化(技术方案 §1.2 / 二期阶段 1 D4)。
 *
 * 属 UI 偏好状态(非业务数据),存 DataStore Preferences 而非 Room。
 * 抽成 interface 便于 ViewModel 单测注入内存实现(真 DataStore 走 IO 线程,
 * 测试 dispatcher 控不了时序);生产用 [SelectedDomainStoreImpl]。
 *
 * 由 [com.growthos.app.di.AppContainer] 持有单例——DataStore 必须进程级唯一,
 * 不能在 Composable / Factory 里反复构造,否则多实例互相覆盖。
 */
interface SelectedDomainStore {
    /** 当前持久化的选中领域 id;null 表示未选过或已清除。 */
    val flow: Flow<Long?>

    /** 写入选中 id;传 null 清除。 */
    suspend fun set(id: Long?)
}

private val Context.selectedDomainDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "selected_domain_prefs"
)

class SelectedDomainStoreImpl(private val context: Context) : SelectedDomainStore {

    private val key = intPreferencesKey("selected_domain_id")

    override val flow: Flow<Long?> = context.selectedDomainDataStore.data.map { prefs ->
        prefs[key]?.takeIf { it > 0 }?.toLong()
    }

    override suspend fun set(id: Long?) {
        context.selectedDomainDataStore.edit { prefs ->
            if (id != null && id > 0) prefs[key] = id.toInt()
            else prefs.remove(key)
        }
    }
}
