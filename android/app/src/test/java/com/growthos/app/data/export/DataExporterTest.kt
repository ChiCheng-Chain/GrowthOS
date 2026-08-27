package com.growthos.app.data.export

import androidx.test.core.app.ApplicationProvider
import com.growthos.app.data.local.GrowthOSDatabase
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.data.local.entity.Knowledge
import com.growthos.app.data.local.entity.Principle
import com.growthos.app.data.local.entity.Sample
import com.growthos.app.data.local.entity.Training
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.ErrorTypeRepository
import com.growthos.app.data.repository.ErrorTypeRepositoryImpl
import com.growthos.app.data.repository.KnowledgeRepository
import com.growthos.app.data.repository.PrincipleRepository
import com.growthos.app.data.repository.SampleRepository
import com.growthos.app.data.repository.TrainingRepository
import com.growthos.app.domain.model.Attribution
import com.growthos.app.domain.model.KnowledgeType
import com.growthos.app.domain.model.TrainingStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [DataExporter] 单测(阶段 7 / 设计 §测试)。
 *
 * 对齐 [com.growthos.app.data.local.GrowthOSDatabaseTest] 范式:Robolectric + in-memory Room DB,
 * 喂真 Repository。验证导出 JSON → decode 回 ExportPayload 五表数据一致 + meta 正确 + 空库不崩。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DataExporterTest {

    private lateinit var db: GrowthOSDatabase
    private lateinit var exporter: DataExporter

    private val fixedNow = 1_700_000_000_000L
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        db = GrowthOSDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        exporter = DataExporterImpl(
            domainRepository = DomainRepository(db.domainDao()),
            errorTypeRepository = ErrorTypeRepositoryImpl(db.errorTypeDao()),
            sampleRepository = SampleRepository(db.sampleDao()),
            trainingRepository = TrainingRepository(db.trainingDao()),
            principleRepository = PrincipleRepository(db.principleDao()),
            knowledgeRepository = KnowledgeRepository(db.knowledgeDao()),
            now = { fixedNow }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `export empty db produces empty tables and valid meta`() = runTest {
        awaitSeed()
        val raw = exporter.export()
        val payload = json.decodeFromString(ExportPayload.serializer(), raw)

        assertTrue("空库领域应空", payload.domains.isEmpty())
        // 错误类型含 R-004 种子 8 个(createInMemory 也挂 SeedCallback)
        assertEquals(8, payload.errorTypes.size)
        assertTrue(payload.samples.isEmpty())
        assertTrue(payload.trainings.isEmpty())
        assertTrue(payload.principles.isEmpty())
        assertTrue(payload.knowledges.isEmpty())
        assertEquals(1, payload.meta.version)
        assertEquals(fixedNow, payload.meta.exportedAt)
    }

    @Test
    fun `export roundtrips all five tables`() = runTest {
        awaitSeed()
        // 注意:ErrorTypeDao 用 INSERT OR IGNORE + 唯一索引,种子已含 8 个常见名。
        // 这里用不重名的错误类型,确保 insert 返回正 id。
        val domainId = db.domainDao().insert(Domain(name = "编程", createdAt = 10L))
        val errorTypeId = insertErrorType("自定义错误A")
        val sampleId = db.sampleDao().insert(
            Sample(
                domainId = domainId, recordedAt = 100L, result = "结果",
                errorTypeId = errorTypeId, attribution = Attribution.CONTROLLABLE,
                emotionIntensity = 4, review = "复盘"
            )
        )
        val trainingId = db.trainingDao().insert(
            Training(
                domainId = domainId, errorTypeId = errorTypeId, goal = "先列状态表",
                acceptanceCriteria = "连续 3 天无该错误", startedAt = 200L, endedAt = null,
                status = TrainingStatus.IN_PROGRESS, note = "备注"
            )
        )
        db.principleDao().insert(
            Principle(
                content = "边界先列清单", createdAt = 300L,
                domainId = domainId, errorTypeId = errorTypeId, trainingId = trainingId, sampleId = sampleId
            )
        )
        db.knowledgeDao().insert(
            Knowledge(
                content = "接杀要先回位", type = KnowledgeType.EXPERIENCE,
                createdAt = 400L, domainId = domainId
            )
        )

        val raw = exporter.export()
        val payload = json.decodeFromString(ExportPayload.serializer(), raw)

        // Domain
        assertEquals(1, payload.domains.size)
        assertEquals("编程", payload.domains.first().name)
        assertEquals(10L, payload.domains.first().createdAt)

        // ErrorType(种子 8 + 自定义 1 = 9)
        assertTrue("应含种子 8 + 自定义 1", payload.errorTypes.size >= 9)
        assertEquals(errorTypeId, payload.errorTypes.first { it.name == "自定义错误A" }.id)

        // Sample
        assertEquals(1, payload.samples.size)
        val s = payload.samples.first()
        assertEquals(sampleId, s.id)
        assertEquals("结果", s.result)
        assertEquals(Attribution.CONTROLLABLE, s.attribution)
        assertEquals(4, s.emotionIntensity)

        // Training
        assertEquals(1, payload.trainings.size)
        val t = payload.trainings.first()
        assertEquals(trainingId, t.id)
        assertEquals(TrainingStatus.IN_PROGRESS, t.status)
        assertEquals("先列状态表", t.goal)
        assertEquals("备注", t.note)

        // Principle
        assertEquals(1, payload.principles.size)
        val p = payload.principles.first()
        assertEquals("边界先列清单", p.content)
        assertEquals(domainId, p.domainId)
        assertEquals(trainingId, p.trainingId)

        // Knowledge
        assertEquals(1, payload.knowledges.size)
        val k = payload.knowledges.first()
        assertEquals("接杀要先回位", k.content)
        assertEquals(KnowledgeType.EXPERIENCE, k.type)
        assertEquals(domainId, k.domainId)

        assertEquals(1, payload.meta.version)
        assertEquals(fixedNow, payload.meta.exportedAt)
    }

    @Test
    fun `export json is human readable pretty printed`() = runTest {
        awaitSeed()
        val raw = exporter.export()
        // prettyPrint 会换行缩进,空库也应含换行
        assertTrue("prettyPrint 应产生换行", raw.contains("\n"))
        assertTrue(raw.contains("\"version\""))
        assertTrue(raw.contains("\"exportedAt\""))
    }

    @Test
    fun `export pulls trainings via observeAllWithNames`() = runTest {
        awaitSeed()
        // 验证 Training 经 JOIN 路径取全(设计:observeAllWithNames().map{it.training})。
        val domainId = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val errorTypeId = insertErrorType("自定义错误B")
        db.trainingDao().insert(
            Training(
                domainId = domainId, errorTypeId = errorTypeId, goal = "g1",
                acceptanceCriteria = null, startedAt = 1L, endedAt = null,
                status = TrainingStatus.IN_PROGRESS, note = null
            )
        )
        db.trainingDao().insert(
            Training(
                domainId = domainId, errorTypeId = errorTypeId, goal = "g2",
                acceptanceCriteria = null, startedAt = 2L, endedAt = 5L,
                status = TrainingStatus.COMPLETED, note = null
            )
        )

        val payload = json.decodeFromString(ExportPayload.serializer(), exporter.export())
        assertEquals(2, payload.trainings.size)
        assertEquals(setOf("g1", "g2"), payload.trainings.map { it.goal }.toSet())
    }

    /** 种子已含 R-004 八个常见名,insert 重名返回 -1,兜底 getByName 取真实 id。 */
    private suspend fun insertErrorType(name: String): Long {
        val id = db.errorTypeDao().insert(ErrorType(name = name, createdAt = 0))
        return if (id > 0) id else db.errorTypeDao().getByName(name)!!.id
    }

    /** 等待 R-004 种子写入完成(onCreate 在写线程异步执行)。 */
    private suspend fun awaitSeed() {
        db.errorTypeDao().observeAll().first { it.size == 8 }
    }
}
