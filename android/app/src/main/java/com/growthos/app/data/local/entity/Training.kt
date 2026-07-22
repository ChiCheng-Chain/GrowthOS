package com.growthos.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.growthos.app.domain.model.TrainingStatus
import kotlinx.serialization.Serializable

/**
 * 训练项(R-010)。针对一个高频/高代价错误建立的阶段性训练目标。
 * 关联 Domain 与 ErrorType,状态机见 [TrainingStatus]。
 */
@Serializable
@Entity(
    tableName = "trainings",
    foreignKeys = [
        ForeignKey(entity = Domain::class, parentColumns = ["id"], childColumns = ["domainId"]),
        ForeignKey(entity = ErrorType::class, parentColumns = ["id"], childColumns = ["errorTypeId"])
    ],
    indices = [
        Index(value = ["domainId"]),
        Index(value = ["errorTypeId"]),
        Index(value = ["status"])
    ]
)
data class Training(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domainId: Long,
    val errorTypeId: Long,
    val goal: String,
    val acceptanceCriteria: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val status: TrainingStatus,
    val note: String?
)
