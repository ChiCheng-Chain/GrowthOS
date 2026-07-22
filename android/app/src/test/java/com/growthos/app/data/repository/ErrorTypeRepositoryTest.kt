package com.growthos.app.data.repository

import androidx.test.core.app.ApplicationProvider
import com.growthos.app.data.local.GrowthOSDatabase
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.data.local.entity.Sample
import com.growthos.app.data.local.entity.Training
import com.growthos.app.domain.model.Attribution
import com.growthos.app.domain.model.TrainingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ErrorTypeRepository.rename] 单测(CRUD 补全:改名 + 撞名合并)。
 *
 * 对齐 [com.growthos.app.data.local.GrowthOSDatabaseTest] 范式:Robolectric + in-memory Room DB。
 * 验证:不撞名直接改名(id 不变);撞名走合并(引用迁移到同名项 + 删旧 id)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ErrorTypeRepositoryTest {

    private lateinit var db: GrowthOSDatabase
    private lateinit var repository: ErrorTypeRepository

    @Before
    fun setup() {
        db = GrowthOSDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        repository = ErrorTypeRepositoryImpl(db.errorTypeDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `rename no collision updates name keeps id`() = runTest {
        awaitSeed()
        val id = insertErrorType("原名")
        repository.rename(id, "新名")

        val et = db.errorTypeDao().getById(id)!!
        assertEquals(id, et.id)
        assertEquals("新名", et.name)
    }

    @Test
    fun `rename to same name is no-op`() = runTest {
        awaitSeed()
        val id = insertErrorType("不变")
        repository.rename(id, "不变")

        assertEquals("不变", db.errorTypeDao().getById(id)!!.name)
    }

    @Test
    fun `rename collision merges sample references and deletes old`() = runTest {
        awaitSeed()
        val domainId = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val keepId = insertErrorType("保留项")
        val mergeId = insertErrorType("待合并")

        // mergeId 有 2 条样本引用,keepId 有 1 条
        db.sampleDao().insert(makeSample(domainId, mergeId, time = 100L))
        db.sampleDao().insert(makeSample(domainId, mergeId, time = 200L))
        db.sampleDao().insert(makeSample(domainId, keepId, time = 300L))

        // 把 mergeId 改名成"保留项"(撞名)→ 合并
        repository.rename(mergeId, "保留项")

        // 旧 id 被删
        assertNull(db.errorTypeDao().getById(mergeId))
        // keepId 仍在,name 不变
        assertEquals("保留项", db.errorTypeDao().getById(keepId)!!.name)
        // mergeId 的 2 条样本引用迁到 keepId:keepId 现有 1+2=3 条
        assertEquals(3, db.errorTypeDao().sampleReferenceCount(keepId))
        assertEquals(0, db.errorTypeDao().sampleReferenceCount(mergeId))
    }

    @Test
    fun `rename collision merges training references`() = runTest {
        awaitSeed()
        val domainId = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val keepId = insertErrorType("保留项")
        val mergeId = insertErrorType("待合并")

        db.trainingDao().insert(
            Training(
                domainId = domainId, errorTypeId = mergeId, goal = "g1",
                acceptanceCriteria = null, startedAt = 100L, endedAt = null,
                status = TrainingStatus.IN_PROGRESS, note = null
            )
        )

        repository.rename(mergeId, "保留项")

        assertNull(db.errorTypeDao().getById(mergeId))
        assertEquals(1, db.errorTypeDao().trainingReferenceCount(keepId))
    }

    @Test
    fun `rename collision with seed name merges into seed`() = runTest {
        awaitSeed()
        val domainId = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val seedId = db.errorTypeDao().getByName("边界条件遗漏")!!.id
        val customId = insertErrorType("自定义")

        db.sampleDao().insert(makeSample(domainId, customId, time = 100L))

        // 把自定义项改名为种子名"边界条件遗漏"→ 合并到种子
        repository.rename(customId, "边界条件遗漏")

        assertNull(db.errorTypeDao().getById(customId))
        assertTrue(db.errorTypeDao().sampleReferenceCount(seedId) >= 1)
    }

    @Test
    fun `rename nonexistent id is no-op`() = runTest {
        awaitSeed()
        repository.rename(99999L, "不存在")
        // 不崩即可
        assertNull(db.errorTypeDao().getById(99999L))
    }

    // ---------- 辅助 ----------

    private suspend fun awaitSeed() {
        db.errorTypeDao().observeAll().first { it.size == 8 }
    }

    private suspend fun insertErrorType(name: String): Long {
        val id = db.errorTypeDao().insert(ErrorType(name = name, createdAt = 0))
        return if (id > 0) id else db.errorTypeDao().getByName(name)!!.id
    }

    private fun makeSample(domainId: Long, errorTypeId: Long, time: Long) = Sample(
        domainId = domainId, recordedAt = time, result = "结果", description = "描述",
        errorTypeId = errorTypeId, attribution = Attribution.CONTROLLABLE,
        emotionIntensity = null, review = "复盘"
    )
}
