package com.growthos.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 原则(R-012)。从多个样本或训练周期沉淀出的可迁移认知。
 *
 * 四个关联字段均为可选软关联,不加外键约束:关联对象被删时原则保留
 * (符合"原则可独立存在"的语义),UI 层显示时对缺失引用做容错。
 */
@Serializable
@Entity(
    tableName = "principles",
    indices = [
        Index(value = ["domainId"]),
        Index(value = ["errorTypeId"])
    ]
)
data class Principle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val createdAt: Long,
    val domainId: Long? = null,
    val errorTypeId: Long? = null,
    val trainingId: Long? = null,
    val sampleId: Long? = null
)
