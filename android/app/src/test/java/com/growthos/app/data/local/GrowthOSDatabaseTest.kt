package com.growthos.app.data.local

import android.database.sqlite.SQLiteException
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.data.local.entity.Principle
import com.growthos.app.data.local.entity.Sample
import com.growthos.app.data.local.entity.Training
import com.growthos.app.data.local.relation.TrainingWithNames
import com.growthos.app.data.local.relation.PrincipleWithNames
import com.growthos.app.domain.model.Attribution
import com.growthos.app.domain.model.Polarity
import com.growthos.app.domain.model.TrainingStatus
import com.growthos.app.util.TimeUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room 数据层单测(技术方案 §10 / 二期计划阶段 0 验收)。
 * Robolectric 提供 Context,in-memory DB 跑,不连真机。
 *
 * 覆盖:TypeConverter 往返、种子数据、§6 四组聚合查询、polarity 统计口径
 * (feature 2026-09-16 / 设计 D4)、v3→v4 无损迁移(设计 D2)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GrowthOSDatabaseTest {

    private companion object {
        const val TEST_DB = "migration-test.db"
    }

    private lateinit var db: GrowthOSDatabase

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GrowthOSDatabase::class.java
    )

    @Before
    fun setup() {
        db = GrowthOSDatabase.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedFactors_insertsTwelveWithPolarityOnFirstCreate() = runTest {
        // R-004 种子(设计 D3):首次建库插入 8 负向 + 4 正向 = 12 条。
        // onCreate 在独立协程跑,首次访问 DAO 触发;在此触发并等待写入完成。
        db.errorTypeDao().observeAll().first { it.size == 12 }
        val seeded = db.errorTypeDao().observeAll().first()
        assertEquals(12, seeded.size)
        assertEquals(
            ErrorTypeSeed.negativeNames.toSet(),
            seeded.filter { it.polarity == Polarity.NEGATIVE }.map { it.name }.toSet()
        )
        assertEquals(
            ErrorTypeSeed.positiveNames.toSet(),
            seeded.filter { it.polarity == Polarity.POSITIVE }.map { it.name }.toSet()
        )
    }

    @Test
    fun attributionConverter_roundTripsAllValues() {
        val converters = Converters()
        for (a in Attribution.entries) {
            val restored = converters.toAttribution(converters.fromAttribution(a))
            assertEquals(a, restored)
        }
    }

    @Test
    fun trainingStatusConverter_roundTripsAllValues() {
        val converters = Converters()
        for (s in TrainingStatus.entries) {
            val restored = converters.toTrainingStatus(converters.fromTrainingStatus(s))
            assertEquals(s, restored)
        }
    }

    @Test
    fun sampleInsert_storesAndRestoresAttributionEnum() = runTest {
        val domainId = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val errorTypeId = insertErrorType("边界条件遗漏")
        val sampleId = db.sampleDao().insert(
            Sample(
                domainId = domainId,
                recordedAt = 1000L,
                result = "线上 bug",
                errorTypeId = errorTypeId,
                attribution = Attribution.CONTROLLABLE,
                emotionIntensity = 4,
                review = "先列状态表再写代码"
            )
        )
        val restored = db.sampleDao().getById(sampleId)!!
        assertEquals(Attribution.CONTROLLABLE, restored.attribution)
        assertEquals(4, restored.emotionIntensity)
    }

    @Test
    fun topErrorTypes_returnsTopThreeByCountDesc() = runTest {
        val domainId = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val e1 = insertErrorType("边界条件遗漏")
        val e2 = insertErrorType("压力下急躁")
        val e3 = insertErrorType("复查不足")
        val e4 = insertErrorType("忽略反馈信号")

        // 出现次数:e1=4, e2=3, e3=2, e4=1 → 前三应为 e1/e2/e3
        insertSamples(domainId, e1, 4, baseTime = 0)
        insertSamples(domainId, e2, 3, baseTime = 1000)
        insertSamples(domainId, e3, 2, baseTime = 2000)
        insertSamples(domainId, e4, 1, baseTime = 3000)

        val top = db.sampleDao().observeTopErrorTypes(domainId, 0L, Long.MAX_VALUE, limit = 3).first()
        assertEquals(listOf(e1, e2, e3), top.map { it.errorTypeId })
        assertEquals(4, top[0].count)
        assertEquals("边界条件遗漏", top[0].errorTypeName)
    }

    @Test
    fun controllableRatio_singleSqlReturnsTotalAndControllable() = runTest {
        val domainId = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val e1 = insertErrorType("边界条件遗漏")
        val e2 = insertErrorType("环境干扰")

        // 4 可控 + 2 不可控 + 1 环境 = total 7, controllable 4
        repeat(4) { i ->
            db.sampleDao().insert(makeSample(domainId, e1, Attribution.CONTROLLABLE, time = 100L + i))
        }
        repeat(2) { i ->
            db.sampleDao().insert(makeSample(domainId, e1, Attribution.UNCONTROLLABLE, time = 200L + i))
        }
        repeat(1) {
            db.sampleDao().insert(makeSample(domainId, e2, Attribution.ENVIRONMENT, time = 300L))
        }

        val ratio = db.sampleDao().observeControllableRatio(domainId, 0L, Long.MAX_VALUE).first()!!
        assertEquals(7, ratio.total)
        assertEquals(4, ratio.controllable)
    }

    @Test
    fun highestEmotion_returnsHighestIntensityNullsLast() = runTest {
        val domainId = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val e1 = insertErrorType("边界条件遗漏")
        val e2 = insertErrorType("复查不足")

        db.sampleDao().insert(makeSample(domainId, e1, Attribution.CONTROLLABLE, intensity = 2, time = 100L))
        db.sampleDao().insert(makeSample(domainId, e1, Attribution.CONTROLLABLE, intensity = 5, time = 200L))
        db.sampleDao().insert(makeSample(domainId, e2, Attribution.CONTROLLABLE, intensity = null, time = 300L))

        val highest = db.sampleDao().observeHighestEmotion(domainId, 0L, Long.MAX_VALUE).first()!!
        assertEquals(5, highest.sample.emotionIntensity)
        assertEquals("边界条件遗漏", highest.errorTypeName)
    }

    @Test
    fun highestEmotion_allNull_returnsNull() = runTest {
        val domainId = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val e1 = insertErrorType("边界条件遗漏")
        db.sampleDao().insert(makeSample(domainId, e1, Attribution.CONTROLLABLE, intensity = null, time = 100L))

        val highest = db.sampleDao().observeHighestEmotion(domainId, 0L, Long.MAX_VALUE).first()
        assertNull(highest)
    }

    @Test
    fun trainingEffectStats_countsBeforeAndAfterStartedAt() = runTest {
        val domainId = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val errorTypeId = insertErrorType("边界条件遗漏")
        val startedAt = 500L

        // 训练前:3 条(< startedAt)
        insertSamples(domainId, errorTypeId, 3, baseTime = 100L)
        // 训练后:2 条(>= startedAt)
        insertSamples(domainId, errorTypeId, 2, baseTime = 600L)

        val stats = db.trainingDao().effectStats(errorTypeId, startedAt)
        assertEquals(3, stats.beforeCount)
        assertEquals(2, stats.afterCount)
    }

    @Test
    fun finishTraining_setsStatusAndEndedAt() = runTest {
        val trainingId = db.trainingDao().insert(
            Training(
                domainId = db.domainDao().insert(Domain(name = "编程", createdAt = 0)),
                errorTypeId = insertErrorType("边界条件遗漏"),
                goal = "先列状态表",
                acceptanceCriteria = null,
                startedAt = 0L,
                endedAt = null,
                status = TrainingStatus.IN_PROGRESS,
                note = null
            )
        )
        val dao = db.trainingDao()
        val t = dao.getById(trainingId)!!
        dao.update(t.copy(status = TrainingStatus.COMPLETED, endedAt = 1000L))

        val finished = dao.getById(trainingId)!!
        assertEquals(TrainingStatus.COMPLETED, finished.status)
        assertEquals(1000L, finished.endedAt)
    }

    @Test
    fun sampleWithNames_joinResolvesErrorTypeAndDomain() = runTest {
        val domainId = db.domainDao().insert(Domain(name = "酒馆战旗", createdAt = 0))
        val errorTypeId = insertErrorType("贪收益导致下限崩盘")
        db.sampleDao().insert(
            makeSample(domainId, errorTypeId, Attribution.CONTROLLABLE, intensity = 3, time = 100L)
        )

        val rows = db.sampleDao().observeWithNames().first()
        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals("酒馆战旗", row.domainName)
        assertEquals("贪收益导致下限崩盘", row.errorTypeName)
    }

    @Test
    fun errorTypeReferenceCount_countsSamplesAndTrainings() = runTest {
        val domainId = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val errorTypeId = insertErrorType("边界条件遗漏")
        db.sampleDao().insert(makeSample(domainId, errorTypeId, Attribution.CONTROLLABLE, time = 100L))
        db.sampleDao().insert(makeSample(domainId, errorTypeId, Attribution.CONTROLLABLE, time = 200L))

        assertEquals(2, db.errorTypeDao().sampleReferenceCount(errorTypeId))
        assertTrue(db.errorTypeDao().trainingReferenceCount(errorTypeId) == 0)
    }

    @Test
    fun sampleUpdate_overwritesFieldsKeepsId() = runTest {
        val domainId = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val errorTypeId = insertErrorType("边界条件遗漏")
        val sampleId = db.sampleDao().insert(
            makeSample(domainId, errorTypeId, Attribution.CONTROLLABLE, intensity = 3, time = 100L)
        )

        val original = db.sampleDao().getById(sampleId)!!
        db.sampleDao().update(
            original.copy(
                result = "改后结果",
                attribution = Attribution.UNCONTROLLABLE,
                review = "改后复盘",
                recordedAt = 999L  // 编辑可改时间(阶段 2 UI 不暴露,但 DAO 层应支持)
            )
        )

        val updated = db.sampleDao().getById(sampleId)!!
        assertEquals(sampleId, updated.id)
        assertEquals("改后结果", updated.result)
        assertEquals(Attribution.UNCONTROLLABLE, updated.attribution)
        assertEquals(999L, updated.recordedAt)
    }

    @Test
    fun sampleDelete_removesRow() = runTest {
        val domainId = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val errorTypeId = insertErrorType("边界条件遗漏")
        val sampleId = db.sampleDao().insert(
            makeSample(domainId, errorTypeId, Attribution.CONTROLLABLE, time = 100L)
        )

        val sample = db.sampleDao().getById(sampleId)!!
        db.sampleDao().delete(sample)

        assertNull(db.sampleDao().getById(sampleId))
    }

    @Test
    fun observeToday_returnsOnlySamplesInTodayWindow() = runTest {
        val domainId = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val errorTypeId = insertErrorType("边界条件遗漏")
        val todayStart = TimeUtil.startOfTodayMillis()
        val yesterday = todayStart - 12 * 3600_000L  // 昨日中午
        val todayNoon = todayStart + 12 * 3600_000L  // 今日中午

        db.sampleDao().insert(makeSample(domainId, errorTypeId, Attribution.CONTROLLABLE, time = yesterday))
        db.sampleDao().insert(makeSample(domainId, errorTypeId, Attribution.CONTROLLABLE, time = todayNoon))

        val today = db.sampleDao()
            .observeToday(TimeUtil.startOfTodayMillis(), TimeUtil.startOfNextDayMillis()).first()
        assertEquals(1, today.size)
        assertEquals(todayNoon, today.first().recordedAt)
    }

    /**
     * 周复盘 F1:observeCount 按时间窗口 + domainId 过滤(阶段 4)。
     * domainId=0 全局,单领域只算该领域;窗口外不计。
     */
    @Test
    fun observeCount_filtersByTimeWindowAndDomain() = runTest {
        val d1 = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val d2 = db.domainDao().insert(Domain(name = "羽毛球", createdAt = 1))
        val e1 = insertErrorType("边界条件遗漏")
        val todayStart = TimeUtil.startOfTodayMillis()
        val day = 24L * 3600_000L

        // 编程:窗口内 3 条(今天 + 3 天前 + 6 天前,均落 7 天),窗口外 1 条(10 天前)
        db.sampleDao().insert(makeSample(d1, e1, Attribution.CONTROLLABLE, time = todayStart))
        db.sampleDao().insert(makeSample(d1, e1, Attribution.CONTROLLABLE, time = todayStart - 3 * day))
        db.sampleDao().insert(makeSample(d1, e1, Attribution.CONTROLLABLE, time = todayStart - 6 * day))
        db.sampleDao().insert(makeSample(d1, e1, Attribution.CONTROLLABLE, time = todayStart - 10 * day))
        // 羽毛球:窗口内 2 条
        db.sampleDao().insert(makeSample(d2, e1, Attribution.CONTROLLABLE, time = todayStart))
        db.sampleDao().insert(makeSample(d2, e1, Attribution.CONTROLLABLE, time = todayStart - 2 * day))

        // 7 天窗口,全局:编程 3 + 羽毛球 2 = 5
        val range7 = TimeUtil.lastNDaysRange(7)
        assertEquals(5, db.sampleDao().observeCount(0, range7.first, range7.last).first())

        // 7 天窗口,单领域编程:3
        assertEquals(3, db.sampleDao().observeCount(d1, range7.first, range7.last).first())

        // 30 天窗口,单领域编程:含 10 天前那条 = 4
        val range30 = TimeUtil.lastNDaysRange(30)
        assertEquals(4, db.sampleDao().observeCount(d1, range30.first, range30.last).first())
    }

    /**
     * 周复盘 F5:observeTopControllableErrorType 只算可控归因,GROUP BY errorTypeId
     * 取频次最高一条(LIMIT 1);无可控错误时返回 null(阶段 4)。
     */
    @Test
    fun topControllableErrorType_returnsHighestAmongControllableOrNull() = runTest {
        val d1 = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val e1 = insertErrorType("边界条件遗漏")  // 4 次可控 → 应为建议关注
        val e2 = insertErrorType("压力下急躁")    // 2 次可控
        val e3 = insertErrorType("环境干扰")      // 5 次但全不可控 → 不计入

        repeat(4) { i -> db.sampleDao().insert(makeSample(d1, e1, Attribution.CONTROLLABLE, time = 100L + i)) }
        repeat(2) { i -> db.sampleDao().insert(makeSample(d1, e2, Attribution.CONTROLLABLE, time = 200L + i)) }
        repeat(5) { i -> db.sampleDao().insert(makeSample(d1, e3, Attribution.UNCONTROLLABLE, time = 300L + i)) }

        val top = db.sampleDao().observeTopControllableErrorType(0, 0L, Long.MAX_VALUE).first()!!
        assertEquals(e1, top.errorTypeId)
        assertEquals("边界条件遗漏", top.errorTypeName)
        assertEquals(4, top.count)

        // 单领域过滤仍正确
        val single = db.sampleDao().observeTopControllableErrorType(d1, 0L, Long.MAX_VALUE).first()!!
        assertEquals(e1, single.errorTypeId)
    }

    @Test
    fun topControllableErrorType_noControllable_returnsNull() = runTest {
        val d1 = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val e1 = insertErrorType("环境干扰")
        // 只有不可控 → 无可控错误 → null
        db.sampleDao().insert(makeSample(d1, e1, Attribution.UNCONTROLLABLE, time = 100L))

        val top = db.sampleDao().observeTopControllableErrorType(0, 0L, Long.MAX_VALUE).first()
        assertNull(top)
    }

    @Test
    fun inProgressTrainingsByDomain_joinResolvesErrorTypeNameAndFiltersStatus() = runTest {
        val domainId = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val otherDomainId = db.domainDao().insert(Domain(name = "羽毛球", createdAt = 1))
        val errorTypeId = insertErrorType("边界条件遗漏")

        // 进行中(应返回)+ 已完成(应过滤)+ 其他领域(应过滤)
        db.trainingDao().insert(
            Training(
                domainId = domainId, errorTypeId = errorTypeId, goal = "先列状态表",
                acceptanceCriteria = null, startedAt = 100L, endedAt = null,
                status = TrainingStatus.IN_PROGRESS, note = null
            )
        )
        db.trainingDao().insert(
            Training(
                domainId = domainId, errorTypeId = errorTypeId, goal = "已完成的训练",
                acceptanceCriteria = null, startedAt = 50L, endedAt = 200L,
                status = TrainingStatus.COMPLETED, note = null
            )
        )
        db.trainingDao().insert(
            Training(
                domainId = otherDomainId, errorTypeId = errorTypeId, goal = "别的领域",
                acceptanceCriteria = null, startedAt = 80L, endedAt = null,
                status = TrainingStatus.IN_PROGRESS, note = null
            )
        )

        val rows = db.trainingDao().observeInProgressByDomainWithTypeName(domainId).first()
        assertEquals(1, rows.size)
        assertEquals("先列状态表", rows.first().training.goal)
        assertEquals("边界条件遗漏", rows.first().errorTypeName)  // JOIN 解析出错误类型名
        assertEquals(TrainingStatus.IN_PROGRESS, rows.first().training.status)
    }

    /**
     * 阶段 5 训练项列表:observeAllWithNames 双 JOIN 解析错误类型名 + 领域名,
     * 按 status(进行中→已完成→已放弃)+ startedAt 倒序排序。
     */
    @Test
    fun observeAllWithNames_joinsNamesAndOrdersByStatusThenStartedAt() = runTest {
        val dProg = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val dBad = db.domainDao().insert(Domain(name = "羽毛球", createdAt = 1))
        val e1 = insertErrorType("边界条件遗漏")
        val e2 = insertErrorType("压力下急躁")

        // id 用显式值便于断言顺序。注意 autoGenerate:传非零 id 直接用。
        db.trainingDao().insert(makeTraining(dProg, e1, TrainingStatus.COMPLETED, startedAt = 100L, id = 1))
        db.trainingDao().insert(makeTraining(dBad, e2, TrainingStatus.IN_PROGRESS, startedAt = 200L, id = 2))
        db.trainingDao().insert(makeTraining(dProg, e1, TrainingStatus.IN_PROGRESS, startedAt = 300L, id = 3))
        db.trainingDao().insert(makeTraining(dBad, e2, TrainingStatus.ABANDONED, startedAt = 400L, id = 4))

        val rows = db.trainingDao().observeAllWithNames().first()
        assertEquals(4, rows.size)
        // 进行中在前(晚的先),然后已完成,最后已放弃
        assertEquals(listOf(3L, 2L, 1L, 4L), rows.map { it.training.id })
        // JOIN 解析名(rows[2] = id=1,已完成,dProg,e1)
        assertEquals("边界条件遗漏", rows[0].errorTypeName)
        assertEquals("编程", rows[0].domainName)
        assertEquals("边界条件遗漏", rows[2].errorTypeName)
        assertEquals("编程", rows[2].domainName)
    }

    /**
     * 阶段 6 原则列表:observeAllWithNames LEFT JOIN 解析领域名+错误类型名,
     * 软关联 null 时名为 null(容错),按 createdAt 倒序。
     */
    @Test
    fun observeAllWithNames_leftJoinsNamesAndHandlesNullSoftRelations() = runTest {
        val dProg = db.domainDao().insert(Domain(name = "编程", createdAt = 0))
        val e1 = insertErrorType("边界条件遗漏")
        // 不建 d2/e2 —— 让 id=2 的原则关联到不存在的领域/错误类型(软关联 null 容错)
        val dNonexistent = 9999L
        val eNonexistent = 8888L

        db.principleDao().insert(Principle(id = 1, content = "早", createdAt = 100L, domainId = dProg, errorTypeId = e1))
        db.principleDao().insert(Principle(id = 2, content = "中", createdAt = 200L, domainId = dNonexistent, errorTypeId = eNonexistent))
        db.principleDao().insert(Principle(id = 3, content = "晚无关联", createdAt = 300L))

        val rows = db.principleDao().observeAllWithNames().first()
        assertEquals(3, rows.size)
        // 按 createdAt 倒序:晚 → 中 → 早
        assertEquals(listOf(3L, 2L, 1L), rows.map { it.principle.id })
        // id=1:正常关联
        assertEquals("编程", rows[2].domainName)
        assertEquals("边界条件遗漏", rows[2].errorTypeName)
        // id=2:关联到不存在的对象(软关联 null 容错,D5)
        assertNull(rows[1].domainName)
        assertNull(rows[1].errorTypeName)
        // id=3:本就无关联
        assertNull(rows[0].domainName)
        assertNull(rows[0].errorTypeName)
    }

    @Test
    fun polarityConverter_roundTripsAllValues() {
        val converters = Converters()
        for (p in Polarity.entries) {
            assertEquals(p, converters.toPolarity(converters.fromPolarity(p)))
        }
    }

    /**
     * 统计口径(设计 D4 / BR-5):成败混合数据下,
     * F1 计数与 F4 情绪峰值含成功样本;F2/F3/分布/effectStats 不含。
     */
    @Test
    fun statistics_withMixedPolarityData_followBRScope() = runTest {
        val domainId = db.domainDao().insert(Domain(name = "酒馆战旗", createdAt = 0))
        val eNeg = insertErrorType("贪收益导致下限崩盘")   // 负向种子
        // 与正向种子「执行到位」重名 → IGNORE 策略返回 -1,走 getByName 兜底(同 insertErrorType 辅助)
        val ePos = insertPositiveFactor("执行到位")

        // 失败样本:负向因素 3 条(可控 2 + 不可控 1),情绪 5 一条
        repeat(2) { i ->
            db.sampleDao().insert(
                makeSample(domainId, eNeg, Attribution.CONTROLLABLE, intensity = 5, time = 100L + i)
            )
        }
        db.sampleDao().insert(
            makeSample(domainId, eNeg, Attribution.UNCONTROLLABLE, intensity = 3, time = 110L)
        )
        // 成功样本:正向因素 2 条(可控优势,情绪 4)
        repeat(2) { i ->
            db.sampleDao().insert(
                makeSample(domainId, ePos, Attribution.CONTROLLABLE_STRENGTH, intensity = 4, time = 200L + i)
            )
        }

        // F1 样本数:含成功 → 5
        assertEquals(5, db.sampleDao().observeCount(0, 0L, Long.MAX_VALUE).first())

        // F4 情绪峰值:含成功 → 情绪 5 的失败样本;若排除成功仍应是它,再断言一次总数兜底
        val emo = db.sampleDao().observeHighestEmotion(0, 0L, Long.MAX_VALUE).first()!!
        assertEquals(5, emo.sample.emotionIntensity)

        // F2 高频错误:只数失败 → 只有 eNeg 榜上有名
        val top = db.sampleDao().observeTopErrorTypes(0, 0L, Long.MAX_VALUE, limit = 10).first()
        assertEquals(listOf(eNeg), top.map { it.errorTypeId })
        assertEquals(3, top[0].count)

        // F3 可控占比:分子分母均只数失败 → total 3, controllable 2
        val ratio = db.sampleDao().observeControllableRatio(0, 0L, Long.MAX_VALUE).first()!!
        assertEquals(3, ratio.total)
        assertEquals(2, ratio.controllable)

        // F5 建议关注:只数失败 → eNeg 可控 2 次
        val f5 = db.sampleDao().observeTopControllableErrorType(0, 0L, Long.MAX_VALUE).first()!!
        assertEquals(eNeg, f5.errorTypeId)
        assertEquals(2, f5.count)

        // 训练效果:本因素(eNeg)前后窗口不受正向样本干扰
        val statsNeg = db.trainingDao().effectStats(eNeg, 105L)
        assertEquals(2, statsNeg.beforeCount)
        assertEquals(1, statsNeg.afterCount)
        // 正向因素被口径排除:训练项永远针对负向因素(产品语义),
        // 正向因素的 effectStats 恒为 0(设计 D4 的边界行为)
        val statsPos = db.trainingDao().effectStats(ePos, 0L)
        assertEquals(0, statsPos.beforeCount)
        assertEquals(0, statsPos.afterCount)
    }

    /** 归因六值(设计 D5):新正向值可入库并还原。 */
    @Test
    fun sampleInsert_storesAndRestoresNewAttributionValues() = runTest {
        val domainId = db.domainDao().insert(Domain(name = "羽毛球", createdAt = 0))
        val errorTypeId = insertErrorType("压力下急躁")

        val id1 = db.sampleDao().insert(
            makeSample(domainId, errorTypeId, Attribution.CONTROLLABLE_STRENGTH, time = 100L)
        )
        val id2 = db.sampleDao().insert(
            makeSample(domainId, errorTypeId, Attribution.OPPONENT_WEAK, time = 200L)
        )
        assertEquals(Attribution.CONTROLLABLE_STRENGTH, db.sampleDao().getById(id1)!!.attribution)
        assertEquals(Attribution.OPPONENT_WEAK, db.sampleDao().getById(id2)!!.attribution)
    }

    /**
     * v3→v4 无损迁移(设计 D2 / BR-8):从 schema 3 起点迁移,
     * polarity 列就位、存量行全 NEGATIVE、正向种子补种、samples 数据保留。
     */
    @Test
    fun migration3To4_preservesDataAndSeedsPositives() {
        val dbv3 = migrationHelper.createDatabase(TEST_DB, 3)
        // v3 结构:error_types 无 polarity 列。造 2 条存量词条 + 1 条样本。
        dbv3.execSQL("INSERT INTO domains (id, name, createdAt, hidden) VALUES (1, '编程', 0, 0)")
        dbv3.execSQL(
            "INSERT INTO error_types (id, name, createdAt) VALUES (1, '存量错误一', 100)"
        )
        dbv3.execSQL(
            "INSERT INTO error_types (id, name, createdAt) VALUES (2, '运气不错', 200)"
        )
        dbv3.execSQL(
            """INSERT INTO samples (id, domainId, recordedAt, result, errorTypeId, attribution,
               emotionIntensity, review) VALUES (1, 1, 500, '旧样本', 1, 'CONTROLLABLE', 3, '旧复盘')"""
        )
        dbv3.close()

        val dbv4 = migrationHelper.runMigrationsAndValidate(TEST_DB, 4, true, GrowthOSDatabase.MIGRATION_3_4)

        // 存量词条保留且全为 NEGATIVE
        dbv4.query("SELECT name, polarity FROM error_types WHERE id IN (1, 2)").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.count)
            while (cursor.moveToNext() || !cursor.isAfterLast) {
                assertEquals("NEGATIVE", cursor.getString(cursor.getColumnIndexOrThrow("polarity")))
                if (cursor.isLast) break
            }
        }

        // 正向种子补种(名字不与存量撞的那部分)
        dbv4.query(
            "SELECT COUNT(*) FROM error_types WHERE polarity = 'POSITIVE' AND name IN ('执行到位','状态良好','判断准确')"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(3, cursor.getInt(0))
        }

        // 样本数据无损
        dbv4.query("SELECT COUNT(*) FROM samples").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        dbv4.close()
    }

    /**
     * 迁移安全(设计 D2):移除 destructive fallback 后,
     * version 4 库不允许无 Migration 的路径——由 Room 校验 schema 一致性兜底。
     * 此处断言 Migration 对象的起点终点。
     */
    @Test
    fun migration3To4_hasCorrectEndpoints() {
        assertEquals(3, GrowthOSDatabase.MIGRATION_3_4.startVersion)
        assertEquals(4, GrowthOSDatabase.MIGRATION_3_4.endVersion)
    }

    // ---------- 辅助 ----------

    /**
     * 插入错误类型并返回真实 id。
     * 种子已含 R-004 八个常见名(如"边界条件遗漏"),ErrorTypeDao 用 INSERT OR IGNORE,
     * 重名时 insert 返回 -1。此处兜底 getByName 取已存在行的 id,避免外键挂 -1。
     */
    private suspend fun insertErrorType(name: String): Long {
        val id = db.errorTypeDao().insert(ErrorType(name = name, createdAt = 0))
        return if (id > 0) id else db.errorTypeDao().getByName(name)!!.id
    }

    /** 插入正向因素并返回真实 id(种子含正向名,IGNORE 策略重名时兜底查回)。 */
    private suspend fun insertPositiveFactor(name: String): Long {
        val id = db.errorTypeDao().insert(
            ErrorType(name = name, createdAt = 0, polarity = Polarity.POSITIVE)
        )
        return if (id > 0) id else db.errorTypeDao().getByName(name)!!.id
    }

    private suspend fun insertSamples(domainId: Long, errorTypeId: Long, count: Int, baseTime: Long) {
        repeat(count) { i ->
            db.sampleDao().insert(
                makeSample(domainId, errorTypeId, Attribution.CONTROLLABLE, time = baseTime + i)
            )
        }
    }

    private fun makeTraining(
        domainId: Long,
        errorTypeId: Long,
        status: TrainingStatus,
        startedAt: Long,
        id: Long = 0
    ) = Training(
        id = id, domainId = domainId, errorTypeId = errorTypeId, goal = "目标",
        acceptanceCriteria = null, startedAt = startedAt, endedAt = null,
        status = status, note = null
    )

    private fun makeSample(
        domainId: Long,
        errorTypeId: Long,
        attribution: Attribution,
        intensity: Int? = 3,
        time: Long
    ) = Sample(
        domainId = domainId,
        recordedAt = time,
        result = "结果",
        errorTypeId = errorTypeId,
        attribution = attribution,
        emotionIntensity = intensity,
        review = "下次怎么做"
    )
}
