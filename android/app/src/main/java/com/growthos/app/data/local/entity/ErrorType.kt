package com.growthos.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 错误类型(R-004)。独立表,跨领域复用;Sample 与 Training 都通过 errorTypeId 引用。
 * [name] 加唯一索引,防止用户重复创建相似错误类型污染统计。
 */
@Serializable
@Entity(
    tableName = "error_types",
    indices = [Index(value = ["name"], unique = true)]
)
data class ErrorType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)
