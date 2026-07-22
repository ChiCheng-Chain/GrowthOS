package com.growthos.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 领域(R-001 / 技术方案 §3.2)。样本与训练项的归属。
 * [hidden] 用于"停用/隐藏暂时不用的领域",不物理删除。
 */
@Serializable
@Entity(tableName = "domains")
data class Domain(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val hidden: Boolean = false
)
