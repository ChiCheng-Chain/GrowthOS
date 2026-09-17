package com.growthos.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.growthos.app.data.local.dao.DomainDao
import com.growthos.app.data.local.dao.ErrorTypeDao
import com.growthos.app.data.local.dao.KnowledgeDao
import com.growthos.app.data.local.dao.PracticeSessionDao
import com.growthos.app.data.local.dao.PrincipleDao
import com.growthos.app.data.local.dao.SampleDao
import com.growthos.app.data.local.dao.TrainingDao
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.data.local.entity.Knowledge
import com.growthos.app.data.local.entity.PracticeSession
import com.growthos.app.data.local.entity.Principle
import com.growthos.app.data.local.entity.Sample
import com.growthos.app.data.local.entity.Training
import com.growthos.app.domain.model.Polarity
import com.growthos.app.util.TimeUtil

/**
 * GrowthOS Room database(技术方案 §3 / §11.2)。
 * 外键约束开启;首次创建时插入 R-004 的 12 个种子关键因素(8 负向+4 正向)。
 *
 * 实例化只在 [com.growthos.app.di.AppContainer] 内做一次,UI 通过 Repository 访问。
 *
 * version 3:Sample 删 description 列(表单合并,feature 2026-08-27)。
 * version 4:error_types 加 polarity 列(关键因素正负,feature 2026-09-16 / 设计 D2)。
 * version 5:新增 practice_sessions 表(练习时间沉淀,feature 2026-09-17 / 设计 D2)。
 * 不再使用 fallbackToDestructiveMigration——用户已有真实数据,升级必须走显式
 * Migration 无损迁移;漏配迁移时宁可崩溃也不静默清库。
 */
@Database(
    entities = [
        Domain::class, ErrorType::class, Sample::class,
        Training::class, Principle::class, Knowledge::class,
        PracticeSession::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class GrowthOSDatabase : RoomDatabase() {
    abstract fun domainDao(): DomainDao
    abstract fun errorTypeDao(): ErrorTypeDao
    abstract fun sampleDao(): SampleDao
    abstract fun trainingDao(): TrainingDao
    abstract fun principleDao(): PrincipleDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun practiceSessionDao(): PracticeSessionDao

    companion object {
        const val DB_NAME = "growthos.db"

        /**
         * v3→v4:error_types 加 polarity 列(存量行 DEFAULT 负向,语义成立——
         * v3 时代词典全是错误类型),补种正向种子(INSERT OR IGNORE + name 唯一索引,幂等)。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE error_types ADD COLUMN polarity TEXT NOT NULL DEFAULT 'NEGATIVE'"
                )
                ErrorTypeSeed.positiveNames.forEach { name ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO error_types (name, createdAt, polarity) VALUES (?, ?, 'POSITIVE')",
                        arrayOf(name, TimeUtil.nowMillis())
                    )
                }
            }
        }

        /**
         * v4→v5:新增 practice_sessions 空表(feature 2026-09-17 / 设计 D2)。
         * DDL 必须与 Room 对 [PracticeSession] 注解的期望逐字段一致(KSP 生成 5.json 兜底),
         * 不触碰存量六表——空表 CREATE 一次到位,无数据回填。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS practice_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        domainId INTEGER NOT NULL,
                        startedAt INTEGER NOT NULL,
                        endedAt INTEGER,
                        note TEXT,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(domainId) REFERENCES domains(id) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_practice_sessions_domainId_startedAt ON practice_sessions (domainId, startedAt)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_practice_sessions_endedAt ON practice_sessions (endedAt)"
                )
            }
        }

        fun create(context: Context): GrowthOSDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                GrowthOSDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .addCallback(SeedCallback())
                .build()

        /** 测试用:内存数据库,主线程允许,便于单测同步读。 */
        fun createInMemory(context: Context): GrowthOSDatabase =
            Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                GrowthOSDatabase::class.java
            )
                .allowMainThreadQueries()
                .addCallback(SeedCallback())
                .build()
    }
}

/**
 * 首次建库时插入 R-004 种子关键因素(8 负向+4 正向,设计 D3)。
 * Room 的 onCreate 在写线程执行,直接用传入的 [db] 同步 execSQL;
 * 不另起协程——SupportSQLiteDatabase 非线程安全,onCreate 的 db 仅在该回调线程有效,
 * 跨协程用会触发 sqlite4java 连接异常,污染后续 insert(外键约束报错)。
 */
private class SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val now = TimeUtil.nowMillis()
        ErrorTypeSeed.all.forEach { (name, polarity) ->
            db.execSQL(
                "INSERT OR IGNORE INTO error_types (name, createdAt, polarity) VALUES (?, ?, ?)",
                arrayOf(name, now, polarity.name)
            )
        }
    }
}
