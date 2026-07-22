package com.growthos.app.data.export

import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.ErrorTypeRepository
import com.growthos.app.data.repository.PrincipleRepository
import com.growthos.app.data.repository.SampleRepository
import com.growthos.app.data.repository.TrainingRepository
import com.growthos.app.util.TimeUtil
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * 数据导出契约(阶段 7 / R-013 / 设计 D6)。
 *
 * 抽成接口便于测试注入桩实现(SettingsViewModelTest 验状态机,不碰真数据层)。
 * 默认实现在各 Repository 拉全量,组装 [ExportPayload],encode 为 JSON 字符串。
 * 调用方(SettingsViewModel)拿到字符串后,经 SAF CreateDocument 写入用户选定的 Uri。
 */
interface DataExporter {
    /** 拉全量五表 + meta → JSON 字符串。空库导出为空列表,不崩。 */
    suspend fun export(): String
}

/**
 * 默认实现:Training 取 [TrainingRepository.observeAllWithNames] 的裸 Training
 * (无单独 observeAll,复用带 JOIN 的查询取 .training,不增量 DAO)。
 */
class DataExporterImpl(
    private val domainRepository: DomainRepository,
    private val errorTypeRepository: ErrorTypeRepository,
    private val sampleRepository: SampleRepository,
    private val trainingRepository: TrainingRepository,
    private val principleRepository: PrincipleRepository,
    private val now: () -> Long = TimeUtil::nowMillis
) : DataExporter {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    override suspend fun export(): String {
        val payload = ExportPayload(
            domains = domainRepository.observeAll().first(),
            errorTypes = errorTypeRepository.observeAll().first(),
            samples = sampleRepository.observeAll().first(),
            trainings = trainingRepository.observeAllWithNames().first().map { it.training },
            principles = principleRepository.observeAll().first(),
            meta = ExportMeta(version = 1, exportedAt = now())
        )
        return json.encodeToString(ExportPayload.serializer(), payload)
    }
}
