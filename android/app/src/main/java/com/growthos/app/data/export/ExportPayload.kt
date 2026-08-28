package com.growthos.app.data.export

import com.growthos.app.data.local.entity.Domain
import com.growthos.app.data.local.entity.ErrorType
import com.growthos.app.data.local.entity.Knowledge
import com.growthos.app.data.local.entity.Principle
import com.growthos.app.data.local.entity.Sample
import com.growthos.app.data.local.entity.Training
import kotlinx.serialization.Serializable

/**
 * 导出文件元信息(阶段 7 D6 / R-013;导入 feature 2026-08-27 必填化)。
 *
 * - [version]:导出格式版本号,导入按版本路由:2=当前(样本无 description);
 *   1=历史(样本含已删的 description,导入时忽略)。必填——缺失即拒绝,
 *   兜住「不支持的备份版本」。
 * - [exportedAt]:导出时刻 epoch millis,便于用户辨认备份新旧。
 */
@Serializable
data class ExportMeta(
    val version: Int,
    val exportedAt: Long
)

/**
 * 导出载荷:六表全量 + meta,序列化为 JSON 写入用户选定位置(R-013)。
 *
 * 实体已加 @Serializable(阶段 7),枚举 Attribution / TrainingStatus 自动支持。
 * 导入侧见 [DataImporter](feature 2026-08-27):v1/v2 兼容,清库重建。
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
