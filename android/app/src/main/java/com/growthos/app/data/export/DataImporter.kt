package com.growthos.app.data.export

import androidx.room.withTransaction
import com.growthos.app.data.local.GrowthOSDatabase
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * 数据导入契约(feature 2026-08-27 导入 JSON 备份 / 设计 D1)。
 *
 * 与 [DataExporter] 对称的接口形态,便于测试注桩(SettingsViewModelTest 验状态机不碰真库)。
 * 两段式:parse 只读不写(校验+预览),apply 才是破坏性写库。
 * 语义 = 恢复:用文件内容替换当前全部数据,保持文件中的主键 id(BR-1/BR-3)。
 */
interface DataImporter {
    /**
     * 解析+校验 JSON 文本,零写库。
     *
     * @throws ImportException 校验失败(格式/版本/语义),[reason] 为面向用户的中文提示
     */
    suspend fun parse(json: String): ImportPreview

    /** 确认后执行:事务内清空六表→按依赖序插入。失败整体回滚,当前数据无损。 */
    suspend fun apply(preview: ImportPreview): ImportCounts
}

/** 校验失败的面向用户异常(reason 即 Snackbar 文案,BR-5/BR-6)。 */
class ImportException(val reason: String) : Exception(reason)

/**
 * parse 的产物:确认框双向对照数据(BR-7)+ 归一化后的载荷。
 * payload 内部持有,apply 直接消费,不经 UI 传递。
 */
class ImportPreview internal constructor(
    internal val payload: ExportPayload,
    val version: Int,
    val exportedAt: Long,
    val backupCounts: TableCounts,
    val currentCounts: TableCounts
)

/** 六表计数(确认框对照与成功反馈共用)。 */
data class TableCounts(
    val domains: Int,
    val errorTypes: Int,
    val samples: Int,
    val trainings: Int,
    val principles: Int,
    val knowledges: Int
)

/** apply 的产物:成功反馈文案数据(BR-6「已导入:N 样本 / M 领域…」)。 */
data class ImportCounts(val tableCounts: TableCounts)

/**
 * 默认实现:直连六 DAO(设计 D1——清库重建是数据层重塑,不走 Repository)。
 *
 * 校验四层(设计 D4):
 * ① 格式层:JSON 解析失败/缺表/坏枚举 → SerializationException;
 * ② 版本层:version ∉ {1,2} 或缺失 → 明确拒绝;
 * ③ 语义预检:每表 id 唯一、sample/training 外键在文件内存在、errorTypes.name 无重复;
 * ④ DB 兜底:事务内约束异常整体回滚。
 *
 * v1/v2 兼容(设计 D2):统一 ignoreUnknownKeys 解码——v1 的 description(及
 * 当前导出器硬编码 1 产出的伪 v1)与一切未知字段天然被忽略。
 */
class DataImporterImpl(
    private val database: GrowthOSDatabase
) : DataImporter {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun parse(jsonText: String): ImportPreview {
        val payload = try {
            json.decodeFromString(ExportPayload.serializer(), jsonText)
        } catch (e: MissingFieldException) {
            throw ImportException("备份文件缺少必要字段(${e.missingFields.firstOrNull() ?: "未知"}),无法导入")
        } catch (e: SerializationException) {
            throw ImportException("文件格式无法识别")
        }
        val version = payload.meta.version
        if (version != 1 && version != 2) {
            throw ImportException("不支持的备份版本(v$version)")
        }
        validate(payload)
        return ImportPreview(
            payload = payload,
            version = version,
            exportedAt = payload.meta.exportedAt,
            backupCounts = payload.counts(),
            currentCounts = database.counts()
        )
    }

    override suspend fun apply(preview: ImportPreview): ImportCounts {
        val p = preview.payload
        database.withTransaction {
            // 清空按反依赖序:子表先清,不违反 FK(设计 D3)
            database.knowledgeDao().deleteAll()
            database.principleDao().deleteAll()
            database.trainingDao().deleteAll()
            database.sampleDao().deleteAll()
            database.errorTypeDao().deleteAll()
            database.domainDao().deleteAll()
            // 插入按依赖序:父表先插(设计 D3 / BR-3)
            database.domainDao().insertAll(p.domains)
            database.errorTypeDao().insertAll(p.errorTypes)
            database.sampleDao().insertAll(p.samples)
            database.trainingDao().insertAll(p.trainings)
            database.principleDao().insertAll(p.principles)
            database.knowledgeDao().insertAll(p.knowledges)
        }
        return ImportCounts(p.counts())
    }

    /** 语义预检(设计 D4 ③):给出精确拒绝理由,不依赖 DB 报文。 */
    private fun validate(payload: ExportPayload) {
        checkUniqueIds("领域", payload.domains.map { it.id })
        checkUniqueIds("错误类型", payload.errorTypes.map { it.id })
        checkUniqueIds("样本", payload.samples.map { it.id })
        checkUniqueIds("训练项", payload.trainings.map { it.id })
        checkUniqueIds("原则", payload.principles.map { it.id })
        checkUniqueIds("知识", payload.knowledges.map { it.id })

        val domainIds = payload.domains.map { it.id }.toSet()
        val errorTypeIds = payload.errorTypes.map { it.id }.toSet()

        payload.samples.firstOrNull { it.domainId !in domainIds }
            ?.let { throw ImportException("样本(id=${it.id})引用了备份中不存在的领域(id=${it.domainId})") }
        payload.samples.firstOrNull { it.errorTypeId !in errorTypeIds }
            ?.let { throw ImportException("样本(id=${it.id})引用了备份中不存在的错误类型(id=${it.errorTypeId})") }
        payload.trainings.firstOrNull { it.domainId !in domainIds }
            ?.let { throw ImportException("训练项(id=${it.id})引用了备份中不存在的领域(id=${it.domainId})") }
        payload.trainings.firstOrNull { it.errorTypeId !in errorTypeIds }
            ?.let { throw ImportException("训练项(id=${it.id})引用了备份中不存在的错误类型(id=${it.errorTypeId})") }

        // error_types.name 唯一索引对应的预检
        payload.errorTypes.groupBy { it.name }.values
            .firstOrNull { it.size > 1 }
            ?.let { throw ImportException("备份中错误类型名称重复(${it.first().name})") }
    }

    private fun checkUniqueIds(tableLabel: String, ids: List<Long>) {
        if (ids.size != ids.toSet().size) {
            throw ImportException("备份中${tableLabel}的 id 重复")
        }
    }

    private fun ExportPayload.counts() = TableCounts(
        domains = domains.size,
        errorTypes = errorTypes.size,
        samples = samples.size,
        trainings = trainings.size,
        principles = principles.size,
        knowledges = knowledges.size
    )

    private suspend fun GrowthOSDatabase.counts() = TableCounts(
        domains = domainDao().countAll(),
        errorTypes = errorTypeDao().countAll(),
        samples = sampleDao().countAll(),
        trainings = trainingDao().countAll(),
        principles = principleDao().countAll(),
        knowledges = knowledgeDao().countAll()
    )
}
