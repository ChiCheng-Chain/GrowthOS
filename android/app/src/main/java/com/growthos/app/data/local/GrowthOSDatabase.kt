package com.growthos.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.growthos.app.data.local.dao.DomainDao
import com.growthos.app.data.local.dao.ErrorTypeDao
import com.growthos.app.data.local.dao.KnowledgeDao
import com.growthos.app.data.local.dao.PrincipleDao
import com.growthos.app.data.local.dao.SampleDao
import com.growthos.app.data.local.dao.TrainingDao
import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.data.local.entity.Knowledge
import com.growthos.app.data.local.entity.Principle
import com.growthos.app.data.local.entity.Sample
import com.growthos.app.data.local.entity.Training
import com.growthos.app.util.TimeUtil

/**
 * GrowthOS Room database(技术方案 §3 / §11.2)。
 * 外键约束开启;首次创建时插入 R-004 的 8 个种子错误类型。
 *
 * 实例化只在 [com.growthos.app.di.AppContainer] 内做一次,UI 通过 Repository 访问。
 *
 * version 2:新增 Knowledge 表。用 fallbackToDestructiveMigration(MVP 阶段,
 * 表结构变更时重建库,种子 onCreate 重新插入;后续如需保留数据再加显式 Migration)。
 */
@Database(
    entities = [
        Domain::class, ErrorType::class, Sample::class,
        Training::class, Principle::class, Knowledge::class
    ],
    version = 2,
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

    companion object {
        const val DB_NAME = "growthos.db"

        fun create(context: Context): GrowthOSDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                GrowthOSDatabase::class.java,
                DB_NAME
            )
                .addCallback(SeedCallback())
                .fallbackToDestructiveMigration()
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
 * 首次建库时插入 R-004 种子错误类型。
 * Room 的 onCreate 在写线程执行,直接用传入的 [db] 同步 execSQL;
 * 不另起协程——SupportSQLiteDatabase 非线程安全,onCreate 的 db 仅在该回调线程有效,
 * 跨协程用会触发 sqlite4java 连接异常,污染后续 insert(外键约束报错)。
 */
private class SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        val now = TimeUtil.nowMillis()
        ErrorTypeSeed.names.forEach { name ->
            db.execSQL(
                "INSERT OR IGNORE INTO error_types (name, createdAt) VALUES (?, ?)",
                arrayOf(name, now)
            )
        }
    }
}
