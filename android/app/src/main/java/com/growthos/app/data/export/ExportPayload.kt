package com.growthos.app.data.export

import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.data.local.entity.Knowledge
import com.growthos.app.data.local.entity.Principle
import com.growthos.app.data.local.entity.Sample
import com.growthos.app.data.local.entity.Training
import kotlinx.serialization.Serializable

/**
 * 导出文件元信息(阶段 7 D6 / R-013)。
 *
 * - [version]:导出格式版本号,后续导入按版本迁移。v2:样本无 description(表单合并,2026-08-27)。
 * - [exportedAt]:导出时刻 epoch millis,便于用户辨认备份新旧。
 */
@Serializable
data class ExportMeta(
    val version: Int = 2,
    val exportedAt: Long
)

/**
 * 导出载荷:五张表全量 + meta,序列化为 JSON 写入用户选定位置(R-013)。
 *
 * 实体已加 @Serializable(阶段 7),枚举 Attribution / TrainingStatus 自动支持。
 * 导入本阶段不做,但格式(含版本号)为后续导入校验预留。
 */
@Serializable
data class ExportPayload(
    val domains: List<Domain>,
    val errorTypes: List<ErrorType>,
    val samples: List<Sample>,
    val trainings: List<Training>,
    val principles: List<Principle>,
    val knowledges: List<Knowledge>,
    val meta: ExportMeta
)
