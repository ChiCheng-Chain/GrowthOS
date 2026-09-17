package com.growthos.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 练习记录(feature 2026-09-17 / 设计 D1):一次练习的起止时间沉淀。
 * [endedAt] 为 null 表示进行中(单条不变式,维护点在 PracticeSessionRepository)。
 * 不挂训练项(BR-1);领域删除无物理路径且 FK 无 CASCADE,与 Sample/Training 同构。
 */
@Serializable
@Entity(
    tableName = "practice_sessions",
    foreignKeys = [
        ForeignKey(entity = Domain::class, parentColumns = ["id"], childColumns = ["domainId"])
    ],
    indices = [
        Index(value = ["domainId", "startedAt"]),
        Index(value = ["endedAt"])
    ]
)
data class PracticeSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domainId: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val note: String? = null,
    val createdAt: Long
)
