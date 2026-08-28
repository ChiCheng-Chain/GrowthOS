package com.growthos.app.di

import android.content.Context
import com.growthos.app.data.export.DataExporter
import com.growthos.app.data.export.DataExporterImpl
import com.growthos.app.data.export.DataImporter
import com.growthos.app.data.export.DataImporterImpl
import com.growthos.app.data.local.GrowthOSDatabase
import com.growthos.app.data.local.SelectedDomainStore
import com.growthos.app.data.local.SelectedDomainStoreImpl
import com.growthos.app.data.repository.DomainRepository
import com.growthos.app.data.repository.ErrorTypeRepository
import com.growthos.app.data.repository.ErrorTypeRepositoryImpl
import com.growthos.app.data.repository.KnowledgeRepository
import com.growthos.app.data.repository.PrincipleRepository
import com.growthos.app.data.repository.SampleRepository
import com.growthos.app.data.repository.TrainingRepository

/**
 * 手动 DI 容器(技术方案 §1.2 / §2)。单 module 小工程不引入 Hilt,
 * Application 持有本容器,UI 经 viewModelScope 取 Repository。
 *
 * Database 懒加载:首次访问才构建(也触发首次启动的种子写入)。
 * [SelectedDomainStore] 同样懒加载——DataStore 必须进程级唯一,由容器持有,
 * 不在 Composable / ViewModel Factory 内构造。
 */
class AppContainer(private val context: Context) {

    private val database: GrowthOSDatabase by lazy { GrowthOSDatabase.create(context) }

    val domainRepository: DomainRepository by lazy { DomainRepository(database.domainDao()) }
    val errorTypeRepository: ErrorTypeRepository by lazy { ErrorTypeRepositoryImpl(database.errorTypeDao()) }
    val sampleRepository: SampleRepository by lazy { SampleRepository(database.sampleDao()) }
    val trainingRepository: TrainingRepository by lazy { TrainingRepository(database.trainingDao()) }
    val principleRepository: PrincipleRepository by lazy { PrincipleRepository(database.principleDao()) }
    val knowledgeRepository: KnowledgeRepository by lazy { KnowledgeRepository(database.knowledgeDao()) }

    val selectedDomainStore: SelectedDomainStore by lazy { SelectedDomainStoreImpl(context) }

    /** 阶段 7 导出:聚合六 Repository 拉全量(R-013)。懒加载,首次导出才构造。 */
    val dataExporter: DataExporter by lazy {
        DataExporterImpl(
            domainRepository = domainRepository,
            errorTypeRepository = errorTypeRepository,
            sampleRepository = sampleRepository,
            trainingRepository = trainingRepository,
            principleRepository = principleRepository,
            knowledgeRepository = knowledgeRepository
        )
    }

    /** 导入 feature 2026-08-27:清库重建走事务直连六 DAO,复用同一 database。懒加载。 */
    val dataImporter: DataImporter by lazy {
        DataImporterImpl(database)
    }
}
