package com.growthos.app.data.local

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [SelectedDomainStoreImpl] 读写往返(二期阶段 1 D4)。
 * DataStore Preferences 在 Robolectric 下走真实文件系统,覆盖真持久化路径。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SelectedDomainStoreTest {

    private fun newStore(): SelectedDomainStoreImpl =
        SelectedDomainStoreImpl(ApplicationProvider.getApplicationContext())

    @Test
    fun set_thenRead_returnsSameId() = runTest {
        val store = newStore()
        store.set(42L)
        assertEquals(42L, store.flow.first())
    }

    @Test
    fun flow_initiallyNull() = runTest {
        val store = newStore()
        assertNull(store.flow.first())
    }

    @Test
    fun set_null_clears() = runTest {
        val store = newStore()
        store.set(7L)
        assertEquals(7L, store.flow.first())
        store.set(null)
        assertNull(store.flow.first())
    }

    @Test
    fun set_nonPositive_ignored() = runTest {
        val store = newStore()
        store.set(0L)
        assertNull(store.flow.first())
        store.set(-1L)
        assertNull(store.flow.first())
    }

    @Test
    fun set_overwritesPrevious() = runTest {
        val store = newStore()
        store.set(1L)
        store.set(2L)
        assertEquals(2L, store.flow.first())
    }
}
