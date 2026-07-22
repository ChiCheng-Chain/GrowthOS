package com.growthos.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.growthos.app.domain.model.Attribution
import kotlinx.serialization.Serializable

/**
 * 样本(R-002 / R-003 / R-006)。MVP 闭环的核心记录。
 *
 * 外键指向 Domain 与 ErrorType,不加 ON DELETE CASCADE(技术方案 §8):
 * 删除领域/错误类型前由 UI 层检查引用,避免误删连锁。
 * 索引对齐技术方案 §3.2:(domainId, recordedAt) 供领域页与列表,errorTypeId 供统计与筛选。
 */
@Serializable
@Entity(
    tableName = "samples",
    foreignKeys = [
        ForeignKey(entity = Domain::class, parentColumns = ["id"], childColumns = ["domainId"]),
        ForeignKey(entity = ErrorType::class, parentColumns = ["id"], childColumns = ["errorTypeId"])
    ],
    indices = [
        Index(value = ["domainId", "recordedAt"]),
        Index(value = ["errorTypeId"])
    ]
)
data class Sample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domainId: Long,
    val recordedAt: Long,
    val result: String,
    val description: String,
    val errorTypeId: Long,
    val attribution: Attribution,
    val emotionIntensity: Int?,
    val review: String
)
