package com.growthos.app.data.local

import androidx.room.TypeConverter
import com.growthos.app.domain.model.Attribution
import com.growthos.app.domain.model.KnowledgeType
import com.growthos.app.domain.model.TrainingStatus

/**
 * Room TypeConverter:枚举以 [Enum.name] 入库(技术方案 §3.3)。
 * 存 name 而非 label,保证改名 label 不破坏已存数据;读时按 name 还原。
 * 未知值(导入旧数据/手改库)兜底抛 IllegalArgumentException,不静默吞错。
 */
class Converters {
    @TypeConverter
    fun fromAttribution(value: Attribution): String = value.name

    @TypeConverter
    fun toAttribution(value: String): Attribution = Attribution.valueOf(value)

    @TypeConverter
    fun fromTrainingStatus(value: TrainingStatus): String = value.name

    @TypeConverter
    fun toTrainingStatus(value: String): TrainingStatus = TrainingStatus.valueOf(value)

    @TypeConverter
    fun fromKnowledgeType(value: KnowledgeType): String = value.name

    @TypeConverter
    fun toKnowledgeType(value: String): KnowledgeType = KnowledgeType.valueOf(value)
}
