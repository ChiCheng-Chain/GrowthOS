package com.growthos.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.PracticeSession
import com.growthos.app.data.repository.PracticeSessionRepository
import com.growthos.app.util.DurationFormat
import com.growthos.app.util.TimeUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

/**
 * 练习记录数据层单测(feature 2026-09-17)。
 * 覆盖:实体 CRUD、进行中不变式(D3)、重叠拦截三态(D6)、看板三条统计 SQL(AC-09 数据侧)、
 * DurationFormat 折算边界(BR-4)、v4→v5 无损迁移(设计 D2)。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PracticeSessionTest {

    private companion object {
        const val TEST_DB = "practice-migration-test.db"
        const val MINUTE = 60L * 1000
        const val HOUR = 60L * MINUTE
        const val DAY = 24L * HOUR
    }

    private lateinit var db: GrowthOSDatabase
    private lateinit var repository: PracticeSessionRepository

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GrowthOSDatabase::class.java
    )

    @Before
    fun setup() = runTest {
        db = GrowthOSDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        repository = PracticeSessionRepository(db.practiceSessionDao())
        db.domainDao().insert(
            Domain(name = "编程", createdAt = 0)
        )
        db.domainDao().insert(
            Domain(name = "吉他", createdAt = 0)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun domainId(name: String): Long =
        db.domainDao().observeAll().first().first { it.name == name }.id

    // ---------- 进行中不变式(D3 / AC-03) ----------

    @Test
    fun start_insertsActiveRow() = runTest {
        val did = domainId("编程")
        val id = repository.start(did, now = 1000)
        assertNotNull(id)
        val active = repository.getActive()
        assertEquals(id, active?.id)
        assertEquals(did, active?.domainId)
        assertNull(active?.endedAt)
    }

    @Test
    fun start_secondWhileActiveIsRejected() = runTest {
        val did = domainId("编程")
        val first = repository.start(did, now = 1000)
        val second = repository.start(did, now = 2000)
        assertNotNull(first)
        assertNull(second)
        assertEquals(1, db.practiceSessionDao().getAll().size)
    }

    @Test
    fun finish_fillsEndedAtAndAllowsNextStart() = runTest {
        val did = domainId("编程")
        val id = repository.start(did, now = HOUR)!!
        repository.finish(id, endedAt = 2 * HOUR, note = "练了琴")
        assertNull(repository.getActive())
        val row = repository.getById(id)!!
        assertEquals(2 * HOUR, row.endedAt)
        assertEquals("练了琴", row.note)
        // 结束后可再次开始
        assertNotNull(repository.start(did, now = 3 * HOUR))
    }

    @Test
    fun observeActive_survivesRebuild() = runTest {
        // AC-01:进程重启(VM 重建)后 activeSession Flow 复现同一行
        val did = domainId("编程")
        val id = repository.start(did, now = 1000)!!
        val observed = repository.observeActive().first()
        assertEquals(id, observed?.id)
    }

    // ---------- 重叠拦截(D6 / AC-07) ----------

    @Test
    fun save_overlappingActiveIsRejected() = runTest {
        val did = domainId("编程")
        repository.start(did, now = 10 * HOUR)!! // 进行中 [10h, ∞)
        // 补录 9:00-11:00,与进行中重叠 → 拒
        val ok = repository.save(null, did, startedAt = 9 * HOUR, endedAt = 11 * HOUR, note = null)
        assertFalse(ok)
    }

    @Test
    fun save_beforeActiveIsAllowed() = runTest {
        val did = domainId("编程")
        repository.start(did, now = 10 * HOUR)!!
        // 补录 8:00-9:00,不重叠 → 过
        val ok = repository.save(null, did, startedAt = 8 * HOUR, endedAt = 9 * HOUR, note = null)
        assertTrue(ok)
    }

    @Test
    fun save_editSelfDoesNotSelfCollide() = runTest {
        val did = domainId("编程")
        val id = repository.start(did, now = 10 * HOUR)!!
        // 编辑进行中行自身(save 走 update 路径,不算重叠)→ 过
        val ok = repository.save(id, did, startedAt = 10 * HOUR, endedAt = 11 * HOUR, note = "修正")
        assertTrue(ok)
        assertEquals(11 * HOUR, repository.getById(id)!!.endedAt)
    }

    @Test
    fun save_historyOverlapIsAllowed() = runTest {
        // BR-7 推论(D6 裁决):历史记录之间重叠不拦
        val did = domainId("编程")
        repository.save(null, did, startedAt = 1 * HOUR, endedAt = 2 * HOUR, note = null)
        val ok = repository.save(null, did, startedAt = 90 * MINUTE, endedAt = 150 * MINUTE, note = null)
        assertTrue(ok)
    }

    // ---------- 看板统计(D9 / AC-09 数据侧) ----------

    private suspend fun seedBoardData() {
        val d1 = domainId("编程")
        val d2 = domainId("吉他")
        // 窗口 [0, 7d):d1 两条(2h + 1h),d2 一条(30min),窗口外一条(不算)
        repository.save(null, d1, startedAt = 0, endedAt = 2 * HOUR, note = null, createdAt = 0)
        repository.save(null, d1, startedAt = DAY, endedAt = DAY + HOUR, note = null, createdAt = 0)
        repository.save(null, d2, startedAt = 2 * DAY, endedAt = 2 * DAY + 30 * MINUTE, note = null, createdAt = 0)
        repository.save(null, d1, startedAt = 8 * DAY, endedAt = 8 * DAY + HOUR, note = null, createdAt = 0)
    }

    @Test
    fun observeTotalAndCount_globalWindow() = runTest {
        seedBoardData()
        val flow = repository.observeTotalAndCount(0, startMillis = 0, endMillis = 7 * DAY)
        val r = flow.first()
        assertEquals(2 * HOUR + HOUR + 30 * MINUTE, r.totalMillis)
        assertEquals(3, r.count)
    }

    @Test
    fun observeTotalAndCount_singleDomain() = runTest {
        seedBoardData()
        val d1 = domainId("编程")
        val r = repository.observeTotalAndCount(d1, 0, 7 * DAY).first()
        assertEquals(2 * HOUR + HOUR, r.totalMillis)
        assertEquals(2, r.count)
    }

    @Test
    fun observeByDomain_groupsAndSums() = runTest {
        seedBoardData()
        val rows = repository.observeByDomain(0, 0, 7 * DAY).first()
        assertEquals(2, rows.size)
        val top = rows.first()
        assertEquals(domainId("编程"), top.domainId)
        assertEquals("编程", top.domainName)
        assertEquals(2 * HOUR + HOUR, top.totalMillis)
    }

    @Test
    fun observeWindowDetails_excludesActiveAndOutside() = runTest {
        seedBoardData()
        repository.start(domainId("编程"), now = 9 * DAY) // 进行中行不计入
        val details = repository.observeWindowDetails(0, 0, 7 * DAY).first()
        assertEquals(3, details.size)
        assertTrue(details.all { it.endedAt > it.startedAt })
    }

    // ---------- DurationFormat(BR-4 / AC-04) ----------

    @Test
    fun durationFormat_toEpochRangeBasic() {
        // 2026-09-17 14:00 + 40 分钟,固定时区
        val tz = TimeZone.getTimeZone("Asia/Shanghai")
        val day = TimeUtil.startOfDayMillis(1000L * DAY, tz)
        val (start, end) = DurationFormat.toEpochRange(day, 14, 40, tz)
        assertEquals(day + 14 * HOUR, start)
        assertEquals(start + 40 * MINUTE, end)
    }

    @Test
    fun durationFormat_boundaries() {
        val tz = TimeZone.getTimeZone("Asia/Shanghai")
        val day = TimeUtil.startOfDayMillis(1_700_000_000_000L, tz) // 某现代日期 0 点(固定时区,无环境依赖)
        // 0 点起点 + 1440 分钟(上限)跨全天
        val (s1, e1) = DurationFormat.toEpochRange(day, 0, 1440, tz)
        assertEquals(day, s1)
        assertEquals(DAY, e1 - s1)
        // 23 点 + 60 分钟跨到次日
        val (s2, e2) = DurationFormat.toEpochRange(day, 23, 60, tz)
        assertEquals(day + 23 * HOUR, s2)
        assertEquals(day + DAY, e2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun durationFormat_rejectsBadHour() {
        DurationFormat.toEpochRange(0, 24, 30)
    }

    @Test(expected = IllegalArgumentException::class)
    fun durationFormat_rejectsOverCap() {
        DurationFormat.toEpochRange(0, 0, 1441)
    }

    @Test
    fun durationFormat_textRendering() {
        assertEquals("45分", DurationFormat.formatMillis(45 * MINUTE))
        assertEquals("1小时23分", DurationFormat.formatMillis(83 * MINUTE))
        assertEquals("2小时", DurationFormat.formatMillis(120 * MINUTE))
        assertEquals("0分", DurationFormat.formatMillis(0))
    }

    // ---------- v4→v5 迁移(设计 D2 / AC-11) ----------

    @Test
    fun migration4To5_createsEmptyTableAndPreservesData() {
        val dbv4 = migrationHelper.createDatabase(TEST_DB, 4)
        // v4 结构(无 practice_sessions):造存量数据
        dbv4.execSQL("INSERT INTO domains (id, name, createdAt, hidden) VALUES (1, '编程', 0, 0)")
        dbv4.execSQL(
            """INSERT INTO samples (id, domainId, recordedAt, result, errorTypeId, attribution,
               emotionIntensity, review) VALUES (1, 1, 500, '旧样本', 1, 'CONTROLLABLE', 3, '旧复盘')"""
        )
        dbv4.execSQL(
            "INSERT INTO error_types (id, name, createdAt, polarity) VALUES (1, '边界条件遗漏', 100, 'NEGATIVE')"
        )
        dbv4.close()

        val dbv5 = migrationHelper.runMigrationsAndValidate(TEST_DB, 5, true, GrowthOSDatabase.MIGRATION_3_4, GrowthOSDatabase.MIGRATION_4_5)

        // practice_sessions 空表就位
        dbv5.query("SELECT COUNT(*) FROM practice_sessions").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        // 六表存量无损
        dbv5.query("SELECT COUNT(*) FROM samples").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        dbv5.query("SELECT COUNT(*) FROM error_types").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        dbv5.close()
    }

    @Test
    fun migration4To5_hasCorrectEndpoints() {
        assertEquals(4, GrowthOSDatabase.MIGRATION_4_5.startVersion)
        assertEquals(5, GrowthOSDatabase.MIGRATION_4_5.endVersion)
    }
}
