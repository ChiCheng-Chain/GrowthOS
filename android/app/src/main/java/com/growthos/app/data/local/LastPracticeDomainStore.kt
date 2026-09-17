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
 * 上次计时用的领域(feature 2026-09-17 / 设计 D5):开始计时选定领域时写入,
 * 练习 Tab 打开时读出做默认选中,省一步。
 *
 * UI 偏好状态(非业务数据),存 DataStore Preferences;与 [SelectedDomainStore] 语义独立
 * (那是领域页选中状态,CE-004:互不覆盖),抽 interface 便于 VM 测试注入内存实现。
 * DataStore 进程级唯一,由 AppContainer 持有单例。
 */
interface LastPracticeDomainStore {
    val flow: Flow<Long?>
    suspend fun set(id: Long?)
}

private val Context.lastPracticeDomainDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "last_practice_domain_prefs"
)

class LastPracticeDomainStoreImpl(private val context: Context) : LastPracticeDomainStore {

    private val key = intPreferencesKey("last_practice_domain_id")

    override val flow: Flow<Long?> = context.lastPracticeDomainDataStore.data.map { prefs ->
        prefs[key]?.takeIf { it > 0 }?.toLong()
    }

    override suspend fun set(id: Long?) {
        context.lastPracticeDomainDataStore.edit { prefs ->
            if (id != null && id > 0) prefs[key] = id.toInt()
            else prefs.remove(key)
        }
    }
}
