# GrowthOS

一个面向个人成长的跨领域复盘与刻意训练系统。

把「记录失误 → 复盘归因 → 建训练项 → 沉淀原则」连成一条闭环,
让每次犯错都能转化为可执行的训练目标,持续沉淀成跨领域复用的原则。

## 功能

- **领域管理**:建立多个成长领域(编程、运动、写作等),领域可隐藏不删,样本归属保留。
- **样本记录**:记录每次失误/反馈——领域、结果、描述、错误类型、归因(可控/不可控/对手/环境)、情绪强度、一句话复盘。
- **领域复盘**:单领域视角看最近样本、错误类型分布、当前训练项、近期原则,支持按错误类型/归因筛选样本。
- **周复盘**:最近 7/14/30 天的高频错误前三、可控占比、情绪强度最高、建议关注的高频可控错误,可跨/单领域切换。
- **训练项**:针对高频错误建立阶段性训练目标,记录训练前后该错误出现次数对比,观察效果。已结束的训练项可物理删除。
- **原则沉淀**:从样本或训练周期沉淀可迁移认知,关联领域/错误类型/训练项/样本,跨样本跨周期复用。
- **错误类型管理**:跨领域复用的错误类型,可改名(撞名自动合并引用)、删除(被引用时拦截)、集中管理。
- **数据导出**:五张表全量导出为 JSON(走系统存储访问框架,无需存储权限),便于备份与迁移。

## 技术栈

| 组件 | 选型 |
|---|---|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3(BOM 2024.12.01) |
| 架构 | 单 Activity + Navigation Compose + MVVM(ViewModel + StateFlow) |
| 数据层 | Room 2.6.1(五张表 + 外键约束 + 聚合查询)+ DataStore(选中领域持久化) |
| 依赖注入 | 手动 DI(AppContainer,单 module 小工程不引入 Hilt) |
| 序列化 | kotlinx-serialization(数据导出) |
| 测试 | JUnit + Robolectric + Room Testing(124 个单测,全绿) |
| 构建 | Gradle 8.10.2 + AGP 8.7.3 + KSP |
| 最低系统 | Android 8.0(API 26)|

## 项目结构

```
android/
├── app/
│   └── src/main/java/com/growthos/app/
│       ├── GrowthOSApp.kt              # Application + AppContainer
│       ├── MainActivity.kt             # 唯一 Activity
│       ├── data/
│       │   ├── local/                  # Room: entity / dao / relation / database / seed
│       │   ├── repository/             # Repository 层(含导出合并逻辑)
│       │   └── export/                 # ExportPayload + DataExporter
│       ├── di/AppContainer.kt          # 手动 DI 容器
│       ├── domain/model/               # Attribution / TrainingStatus 枚举
│       └── ui/
│           ├── components/             # 账本式复用组件(Ledger/发丝线/眉标)
│           ├── theme/                  # 主题与字号
│           ├── navigation/             # 路由 + 底部导航
│           ├── record/                 # 记录 Tab:录入页 + 今日列表
│           ├── domain/                 # 领域页:领域切换 + 编辑对话框
│           ├── domain_view/            # 领域 Tab:四区块复盘 + 样本筛选
│           ├── weekly/                 # 复盘 Tab:周复盘 + 子页面入口
│           ├── training/               # 训练项:列表 / 编辑 / 效果观察
│           ├── principle/              # 原则:列表 / 编辑
│           ├── error_type/             # 错误类型:列表 / 改名 / 删除
│           └── settings/               # 设置:数据导出(SAF)
└── gradle/libs.versions.toml           # 依赖版本集中管理
```

## 构建

本机没有 `gradlew`(只有 wrapper properties),用缓存的 gradle 二进制:

```bash
# Windows(用 Android Studio 自带 JBR 21)
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
"C:\Users\%USERNAME%\.gradle\wrapper\dists\gradle-8.10.2-bin\9x0at1uld387do03q0roict4h\gradle-8.10.2\bin\gradle" -p android :app:assembleRelease
```

产物:`android/app/build/outputs/apk/release/app-release.apk`(约 1.5 MB,R8 混淆 + 正式签名)。

## 测试

```bash
gradle -p android :app:testDebugUnitTest
```

15 个测试类、124 个单测,覆盖数据层序列化往返、Repository 改名合并逻辑、各 ViewModel 状态机、导出流程。

## 设计理念

- **账本式 UI**:发丝线分隔的行式版面,不用 Material 默认的阴影 Card,让页面读起来像一本翻开的手册。
- **闭环优先**:每个功能都接真数据(Repository → Room),不做 mock 占位;Preview 只用于 UI 组件开发。
- **删除安全**:错误类型删除走引用检查(被样本/训练项引用则拦截);样本/原则删除带确认;领域只隐藏不物理删除(保留历史归属);训练项结束即归档不可篡改。

## License

私有项目,未开源。
