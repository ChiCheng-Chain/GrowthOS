package com.growthos.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.growthos.app.domain.model.Polarity
import kotlinx.serialization.Serializable

/**
 * 错误类型——UI 名称「关键因素」(R-004 / feature 2026-09-16 BR-1)。
 * 独立表,跨领域复用;Sample 与 Training 都通过 errorTypeId 引用。
 * [name] 加唯一索引,防止用户重复创建相似因素污染统计。
 *
 * [polarity](设计 D1):负向=错误,正向=做对的事;样本成败由它推导(BR-2)。
 * 类名/表名不改(设计 BR-9「只改 UI 文案」决策),JSON 字段名 errorTypeId 不变。
 * polarity 带 NEGATIVE 默认值:旧版 v1/v2 备份 JSON 无此字段,导入时天然回填负向(设计 D9)。
 */
@Serializable
@Entity(
    tableName = "error_types",
    indices = [Index(value = ["name"], unique = true)]
)
data class ErrorType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val polarity: Polarity = Polarity.NEGATIVE
)
