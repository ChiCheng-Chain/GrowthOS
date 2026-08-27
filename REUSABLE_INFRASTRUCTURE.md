# GrowthOS 可复用基础设施指南

> 本文从 GrowthOS 项目中提炼出与业务无关、可被新 Android 仓库直接复用的工程基础设施。
> 适用于：单 module、Kotlin 2.0 + Jetpack Compose + Room + Compose Navigation、不引入 Hilt 的小型工程。
>
> 调研日期：2026-07-23｜源仓库：GrowthOS（commit `b7d5b4b`）

---

## 目录

1. [技术栈与版本基线](#1-技术栈与版本基线)
2. [构建配置（可直接复制的文件）](#2-构建配置可直接复制的文件)
3. [签名与发布](#3-签名与发布)
4. [项目骨架与包结构](#4-项目骨架与包结构)
5. [手动 DI 范式](#5-手动-di-范式)
6. [数据层范式（Room + Repository）](#6-数据层范式room--repository)
7. [UI 主题与字体](#7-ui-主题与字体)
8. [通用组件库](#8-通用组件库)
9. [导航范式](#9-导航范式)
10. [ViewModel 与状态管理](#10-viewmodel-与状态管理)
11. [数据导出框架](#11-数据导出框架)
12. [单元测试范式](#12-单元测试范式)
13. [新仓库落地顺序](#13-新仓库落地顺序)
14. [复用清单速查](#14-复用清单速查)

---

## 1. 技术栈与版本基线

| 项 | 值 | 说明 |
|---|---|---|
| Gradle | 8.10.2 | 华为云镜像分发 |
| AGP | 8.7.3 | |
| Kotlin | 2.0.21 | Compose Compiler 走独立插件 |
| KSP | 2.0.21-1.0.28 | Room 编译器 |
| JDK | 17 | source/targetCompatibility + jvmTarget |
| compileSdk / targetSdk | 36 | 见下方注意事项 |
| minSdk | 26 | 覆盖 Android 8.0+ |
| Compose BOM | 2024.12.01 | |
| Navigation Compose | 2.8.5 | |
| Room | 2.6.1 | KSP 编译，exportSchema=true |
| DataStore Preferences | 1.1.1 | |
| kotlinx.serialization | 1.7.3 | 导出/导入用 |
| Coroutines | 1.9.0 | |

**注意事项**

- AGP 8.7.3 官方未正式测试 compileSdk=36，GrowthOS 靠 `gradle.properties` 里 `android.suppressUnsupportedCompileSdk=36` 压住警告。新仓库若不需要 Android 16 新 API，建议 compileSdk/targetSdk 降到 35 并删掉该抑制项。
- Kotlin 2.0+ 的 Compose Compiler 是独立插件 `org.jetbrains.kotlin.plugin.compose`，不再是 Kotlin 编译器的内置功能。

---

## 2. 构建配置（可直接复制的文件）

以下文件业务无关，换仓库可整体复制后只改包名/项目名：

### 2.1 `android/gradle/libs.versions.toml`

完整版本目录，管理全部依赖与插件版本。按类别组织：Compose / Lifecycle / Navigation / Room / DataStore / Serialization / Coroutines / Test。**可整体复制**，按需增删依赖条目。

### 2.2 `android/build.gradle`（项目级）

仅 4 个插件 alias（android.application / kotlin.android / kotlin.serialization / ksp / kotlin.compose），完全通用。

### 2.3 `android/settings.gradle`

- `pluginManagement` 与 `dependencyResolutionManagement` 把阿里云镜像放在 `google()`/`mavenCentral()` 之前（国内加速），并设 `FAIL_ON_PROJECT_REPOS`。
- 海外仓库可删掉前三行阿里云镜像。
- 复制后改 `rootProject.name`。

### 2.4 `android/gradle.properties`

JVM 参数、AndroidX 开关、caching、Kotlin 代码风格，通用。`android.suppressUnsupportedCompileSdk=36` 按需保留或删除。

### 2.5 `android/gradle/wrapper/gradle-wrapper.properties`

仅 Gradle 版本 + 镜像 URL。

> ⚠️ **GrowthOS 缺 `gradlew`/`gradlew.bat`/`gradle-wrapper.jar`**，只有 properties 文件。新仓库**务必补齐 wrapper 四件套**（执行 `gradle wrapper` 生成），否则无法可复现构建。

### 2.6 `android/.gitignore`

标准 Android 忽略规则（含 keystore / schemas / logs / local.properties），通用。

### 2.7 `app/build.gradle` 的结构性部分

可复用的结构：
- `plugins` 块（5 个 alias）
- `android {}` 的 compileOptions / kotlinOptions / buildFeatures.compose / packagingOptions / testOptions
- `signingConfigs` 的**容错读取逻辑**（见下节）
- `buildTypes.release` 的 `minifyEnabled + shrinkResources + proguardFiles` 组合
- `ksp { arg 'room.schemaLocation' ... }`
- `dependencies` 按类别引入的方式

需改的业务部分：`namespace`、`applicationId`。

### 2.8 ProGuard 规则（`proguard-rules.pro`）

**通用模板段（直接复制）**：
- kotlinx-serialization 段：保留 `@Serializable` 类的 Companion/INSTANCE 及 `serializer(...)`、保留 `*Annotation*`、InnerClasses。
- 枚举段：通用保留 `values()`/`valueOf(String)`。

**需改造段**：
- Room 段：包路径 `com.growthos.app.data.local.*`、类名 `GrowthOSDatabase`/`Converters` → 换成新包名/类名。
- 应用入口段：`GrowthOSApp`/`MainActivity` → 换成新类名。

### 2.9 `AndroidManifest.xml` 骨架

无任何权限声明，`application` 配 `android:name` + 单个 `exported=true` 的 launcher Activity。非常精简，通用。改 `android:name`、`label`、`theme`、`icon` 引用即可。

---

## 3. 签名与发布

### 3.1 keystore 容错配置（可复用范式）

`app/build.gradle` 在 `android {}` 开头读取 `keystore.properties`，**文件存在才加载、才填充 signingConfigs.release**；不存在则跳过，release 产出未签名 APK。这套容错逻辑可直接复用：

```groovy
def keystoreProperties = new Properties()
def keystorePropsFile = rootProject.file("keystore.properties")
if (keystorePropsFile.exists()) {
    keystoreProperties.load(new FileInputStream(keystorePropsFile))
}

signingConfigs {
    release {
        if (keystorePropsFile.exists()) {
            storeFile rootProject.file(keystoreProperties['storeFile'])
            storePassword keystoreProperties['storePassword']
            keyAlias keystoreProperties['keyAlias']
            keyPassword keystoreProperties['keyPassword']
        }
    }
}
```

`keystore.properties`（gitignore，不入库）含四项：`storeFile` / `storePassword` / `keyAlias` / `keyPassword`。

> ⚠️ **GrowthOS 的安全隐患**：`.gitignore` 写了忽略 `keystore.properties` 和 `*.keystore`，但这俩文件实际存在于工作区（含明文口令）。新仓库**切勿照搬密钥**，应重新生成自己的 keystore，并确保只留本地不入库。

### 3.2 release 构建命令

```bash
gradle :app:assembleRelease --console=plain
```

产物：`app/build/outputs/apk/release/app-release.apk`（GrowthOS 约 1.7 MB，开了 R8 + 资源压缩）。

### 3.3 GitHub Releases 分发

APK 不进 git 历史，挂到 tag 下的 Release 作为 asset：
1. `git tag -a v0.1.0 -m "..."` → `git push origin v0.1.0`
2. GitHub 网页为该 tag 创建 Release，上传 APK 作为附件
3. 用户访问 `releases` 页直接下载装机

进阶：配 GitHub Actions 在打 tag 时自动 `assembleRelease` + 上传（需用 Secrets 注入 keystore，不能明文进仓库）。

---

## 4. 项目骨架与包结构

经典五层划分（单 module）：

```
com.example.app/
├── ExampleApp.kt                    # Application，持有 container
├── MainActivity.kt                  # setContent { Theme { Surface { AppRoot() } } }
│
├── di/
│   └── AppContainer.kt              # by lazy 持有 database + 各 repository
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt           # @Database + create()/createInMemory() + SeedCallback
│   │   ├── Converters.kt            # 枚举 TypeConverter
│   │   ├── dao/
│   │   ├── entity/
│   │   └── relation/                # @Embedded JOIN 投影（有 JOIN 才需要）
│   └── repository/
│
├── domain/
│   └── model/                       # 纯枚举/值对象（被 entity 复用 + Converters 落库）
│
├── ui/
│   ├── navigation/
│   │   └── AppRoot.kt               # Tab sealed class + Routes object + NavHost + 底部栏
│   ├── components/                  # 跨页复用纯展示组件
│   ├── theme/
│   │   ├── Theme.kt
│   │   └── Type.kt
│   └── <feature>/                   # 每个 feature 一个包
│       ├── XxxScreen.kt            # 顶层 Screen + Content + Preview
│       └── XxxViewModel.kt         # UiState + ViewModel + 内嵌 Factory
│
└── util/
    └── TimeUtil.kt                  # 纯 object 工具
```

**各层职责**：
- `data.local` — Room：entity（表）/ dao（查询）/ relation（JOIN 投影）/ Database / Converters / DataStore 封装
- `data.repository` — UI 的唯一数据入口，薄包装 DAO
- `domain.model` — 纯枚举/值对象（本项目没有 use-case / repository 接口，domain 很薄）
- `ui.<feature>` — 每个功能一个包，含 Screen + ViewModel
- `ui.components` — 跨页纯展示组件
- `ui.theme` — 配色 + 字体
- `ui.navigation` — 唯一的 NavHost 与路由
- `di` — 手动 DI 容器
- `util` — 无状态工具 object

> 本项目业务逻辑实际落在 ViewModel 与 Repository，domain 层只放枚举。这是小工程的合理选择，不必强加 use-case 层。

---

## 5. 手动 DI 范式

零注解框架（不引入 Hilt），三件套：

### 5.1 Application 持有容器

```kotlin
class ExampleApp : Application() {
    lateinit var container: AppContainer
        private set
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

通过 `AndroidManifest.xml` 的 `android:name=".ExampleApp"` 注册。容器是进程级单例。

### 5.2 AppContainer：唯一组合根

```kotlin
class AppContainer(context: Context) {
    val database by lazy { AppDatabase.create(context) }
    val xxxRepository by lazy { XxxRepository(database.xxxDao()) }
    // ...每个 repository 一行
}
```

- 所有依赖 `by lazy`，首次访问才实例化（Database 首次访问才建库 + 触发 seed）。
- **进程级唯一对象必须放容器**：Room Database、DataStore。注释强调"DataStore 必须进程级唯一，不能在 Composable/Factory 里反复构造"——这是硬性约束。
- 聚合多 Repository 的对象（如导出器）也 `by lazy` 放容器。

### 5.3 Screen 取 container 构造 ViewModel

每个 Screen 开头固定三行：

```kotlin
val container = (LocalContext.current.applicationContext as ExampleApp).container
val vm: XxxViewModel = viewModel(factory = XxxViewModel.Factory(container.xxxRepository))
val state by vm.uiState.collectAsStateWithLifecycle()
```

`MainActivity` 不注入任何东西，只 `setContent { Theme { Surface { AppRoot() } } }`。

---

## 6. 数据层范式（Room + Repository）

### 6.1 RoomDatabase 配置

```kotlin
@Database(entities = [...], version = N, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun xxxDao(): XxxDao
    // ...每个 DAO 一个 abstract fun

    companion object {
        const val DB_NAME = "app.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .addCallback(SeedCallback())
                .fallbackToDestructiveMigration()
                .build()

        fun createInMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, AppDatabase::class.java)
                .allowMainThreadQueries()
                .addCallback(SeedCallback())
                .build()
    }
}
```

要点：
- `@TypeConverters` 标在 class 上，全库共用一个 `Converters`。
- 数据库单例不放 Database 文件，放 `AppContainer` 用 `by lazy` 持有。
- 两个静态工厂：`create`（落盘 + seed + fallback）/ `createInMemory`（测试用内存库 + allowMainThreadQueries）。
- `applicationContext` 传入 builder 防止 Activity 泄漏。
- `exportSchema=true` 为将来转显式 Migration 留 schema 快照。

### 6.2 TypeConverter 范式

枚举统一存 `Enum.name`（String），不存 label。读时 `EnumType.valueOf(name)` 还原。**未知值不静默兜底**，`valueOf` 直接抛 `IllegalArgumentException`。一类枚举写一对 `fromXxx`/`toXxx`，集中在同一个 `Converters` 类。

> 注释明确"存 name 而非 label，保证改名 label 不破坏已存数据"。

### 6.3 DAO 范式

返回值约定（核心）：
- **观察型**：`fun observeXxx(): Flow<List<T>>`（非 suspend，持续推流），UI 侧 `collectAsStateWithLifecycle`。
- **一次性读取**：`suspend fun getXxx(): T?`。
- 同一实体常同时提供 `getById`（suspend）和 `observeById`（Flow）。

写操作：
- `@Insert(onConflict = ABORT/IGNORE) suspend fun insert(): Long`（返回 rowId）
- `@Update suspend fun update()`
- `@Delete suspend fun delete()`
- 局部更新走 `@Query("UPDATE ... SET ... WHERE id=:id") suspend fun`（如 `setHidden`），不整对象 update。

JOIN 查询：**手写 SQL 写在 `@Query` 里**，返回 `Flow<List<关系POJO>>`。LEFT JOIN 处理软关联可 null。不用 `@Relation`（那是带 `@Transaction` 的 N+1 自动查询）。

排序：列表 observe 一律带 `ORDER BY createdAt ASC/DESC`，约定写在 DAO 而非 UI。

引用计数校验也放 DAO：`@Query("SELECT COUNT(*) FROM ... WHERE fk=:id") suspend fun xxxReferenceCount(): Int`，删除前由 Repository 聚合判断。

### 6.4 实体范式

```kotlin
@Entity(tableName = "xxxs")  // 表名统一复数蛇形
@Serializable                 // kotlinx.serialization，便于导出/导入
data class Xxx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,      // epoch millis，由 Repository 注入
    // ...
)
```

- 主键统一 `Long` 自增，默认 0 让 Room 填充。
- 时间戳统一 `Long`（epoch millis），字段名 `createdAt`/`recordedAt`，由 `TimeUtil.nowMillis()` 在 Repository 层注入，实体不带默认。
- 外键二档策略：
  - **硬关联**：配 `ForeignKey` + `Index`，**不加 ON DELETE CASCADE**（删除前 UI 层查引用计数拦截）。
  - **软关联**：关联字段用 `Long? = null`，**不加外键约束**，只加 `Index`，关联对象删除时本行保留、UI 容错 null。
- 组合索引用 `Index(value = ["domainId", "recordedAt"])`，按查询路径建。

### 6.5 关系类范式

```kotlin
data class XxxWithYyy(
    @Embedded val xxx: Xxx,
    val yyyName: String?        // JOIN 出来的列，可 null 配 LEFT JOIN
)
```

不用 `@Relation`，统一**手写 JOIN SQL + `@Embedded` + 平铺列**，一次查询取全。纯聚合统计结果（如计数）也是普通 data class，对应 `@Query` 的 SELECT 投影列名对齐字段名。

### 6.6 Repository 范式

绝大多数是**薄包装**：
- 构造器收 `private val dao: XxxDao`
- `observeXxx()` 直接 `return dao.observeXxx()`（Flow 原样透传，不做 map/转换）
- `getXxx()` 直接转调

Repository 承担的"非薄"职责集中在两点：
1. **领域对象构造 + 时间戳注入**：`create(...)` 方法收业务参数，内部 `TimeUtil.nowMillis()` 填 `createdAt` 再 `dao.insert`，不暴露裸实体构造给 VM。
2. **读-改-写**：`setDone`/`rename` 先 `dao.getById` 再 `copy` 再 `update`。

**接口/实现分离是例外而非默认**：只有逻辑复杂或需单测注入桩的 Repository 才抽 `interface + Impl`（GrowthOS 仅 `ErrorTypeRepository` 如此）；其余都是具体类。

不在 Repository 里切 Dispatcher（Room 自带 IO 调度），ViewModel 用 `viewModelScope.launch` 调 suspend。

### 6.7 迁移策略

- MVP 期用 `fallbackToDestructiveMigration()`，表结构变更时重建库，种子 `onCreate` 重新插入。
- 没有任何 `Migration` 对象。
- `exportSchema=true` 已开，为将来加显式 Migration 留路。

### 6.8 种子数据范式

`RoomDatabase.Callback.onCreate` 内**同步** `execSQL("INSERT OR IGNORE ...")`，不另起协程（SupportSQLiteDatabase 非线程安全，跨协程会触发连接异常）。种子用 `INSERT OR IGNORE` 配合 `@Insert(IGNORE)` 幂等。

### 6.9 TimeUtil 思路

数据层只存 `Long` epoch millis，窗口计算（今日/最近 N 天）集中到一个 `object TimeUtil`，UI 层负责格式化。这个文件本身偏业务但模式可复用。

---

## 7. UI 主题与字体

### 7.1 theme 包结构

GrowthOS 的 `theme/` 只有两个文件（没有独立 Color.kt / Shape.kt，颜色和 Shapes 内联在 Theme.kt）：
- `Theme.kt` — 颜色常量、`LightColors`/`DarkColors` 双套 ColorScheme、`LedgerShapes`、入口 `XxxTheme` Composable
- `Type.kt` — Google Fonts provider、字体族、Typography

### 7.2 配色方案

**自定义品牌色，非 Material 默认**。GrowthOS 的语义是"复盘手册/账本"，新仓库应换成自有品牌色。结构可复用：
- `LightColors`/`DarkColors` 双套 `lightColorScheme`/`darkColorScheme` 全量映射，跟随 `isSystemInDarkTheme()`。
- **动态取色：支持但默认关闭**。`XxxTheme(dynamicColor: Boolean = false)`，开关打开且 Android 12+ 时走 `dynamicLightColorScheme`/`dynamicDarkColorScheme`。

### 7.3 字体

依赖 `androidx.compose.ui.text.google.fonts`，运行时下载，无需打包字体文件。

GrowthOS 用"双族"思路（新仓库可保留思路换字体）：
- **衬线字体**（标题/金句）——手册稳重感
- **等宽字体**（数字/时间/标签）——账本符号感
- 正文不指定 fontFamily，回落系统 Sans

引入方式：`GoogleFont.Provider` + `R.array.com_google_android_gms_fonts_certs`，证书数组定义在 `res/values/font_certs.xml`（dev + prod 两套）。**新仓库复用字体方案时必须一并拷贝该 XML。**

### 7.4 Shapes

GrowthOS 除输入框 2dp 外全直角（服务于"账本感"）。这是产品定位产物，新仓库按自己的设计语言调整。

---

## 8. 通用组件库

GrowthOS 的 `ui/components/Ledger.kt` 含 7 个账本风格组件，**全部只依赖 MaterialTheme 语义 token，无业务耦合，可整文件拷走**（换设计语言时改色值/形状即可）：

| 组件 | 作用 |
|---|---|
| `LedgerRule` | 全宽 0.5dp 发丝分隔线 |
| `Eyebrow` | 章节眉标（等宽小字 + 可选序号） |
| `PageHeader` | 页面标题块（眉标 + 大标题 + 副说明） |
| `LedgerMetric` | 大数字 + 标签的指标行（数字用等宽） |
| `LedgerRow` | 行式条目（leading/trailing slot + 底部分割线） |
| `NextActionBlock` | "下次怎么做"区块（琥珀色左边框） |
| `DistributionBar` | 横向条形（分布统计，不依赖图表库） |

### 8.1 页面外壳范式

标准列表页写法（`KnowledgeListScreen` / `PrincipleListScreen` 几乎逐行对齐）：

```kotlin
@Composable
fun XxxScreen(onBack: () -> Unit, onOpenEdit: (Long?) -> Unit, onOpenCreate: () -> Unit) {
    val container = (LocalContext.current.applicationContext as ExampleApp).container
    val vm: XxxViewModel = viewModel(factory = XxxViewModel.Factory(container.xxxRepository))
    val state by vm.uiState.collectAsStateWithLifecycle()
    XxxContent(state, onBack, onOpenEdit, onOpenCreate, ...)
}

@Composable
private fun XxxContent(state: XxxUiState, ...) {
    var deleting by remember { mutableStateOf<Xxx?>(null) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("标题", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(ArrowBack, "返回") } },
                actions = { IconButton(onClick = onOpenCreate) { Icon(Add, "新建") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(innerPadding).verticalScroll(rememberScrollState())
        ) {
            state.filteredItems.forEach { item ->
                XxxRow(item, onClick = { onOpenEdit(item.id) }, ...)
                LedgerRule(modifier = Modifier.padding(horizontal = 20.dp))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
    // 删除确认 AlertDialog
}
```

**Padding 约定**：内容横向统一 `20.dp`；行内 `vertical=14.dp`；`PageHeader` 用 `vertical=16.dp`；空提示 `vertical=24.dp`；筛选条 `vertical=10.dp`。

**列表行 + 分割线范式**：两种并存——通用版 `LedgerRow`（slot + `showDivider`）；业务版各页自定义 `XxxRow`（`Surface(onClick)` 包裹 + 行后手动补 `LedgerRule`）。都是"石灰纸底 + 发丝线分隔"，刻意不用 Material 阴影 Card。

---

## 9. 导航范式

定义于 `ui/navigation/AppRoot.kt`，"单 NavHost + 底部 Tab + 顶层 Routes object"。

### 9.1 Tab 用 sealed class

```kotlin
private sealed class Tab(val route: String, val label: String, val icon: ImageVector)
private data object Record : Tab("record", "记录", Icons.Outlined.Edit)
private data object Domains : Tab("domains", "领域", Icons.Outlined.Category)
// ...
private val tabs = listOf(Record, Domains, ...)
```

Tab 本身就是 NavHost 的 `composable` route。

### 9.2 Routes object 集中管路由

```kotlin
object Routes {
    const val SAMPLE_EDIT = "sample_edit"
    const val SAMPLE_EDIT_WITH_ID = "sample_edit?sampleId={sampleId}"
    fun sampleEdit(sampleId: Long? = null): String {
        val id = sampleId?.takeIf { it > 0 } ?: -1L
        return "sample_edit?sampleId=$id"
    }
}
```

带参路由核心写法：单一函数同时服务"新建"（id 为 null/非正 → 拼无参串）与"编辑"（拼实参串）。

### 9.3 NavHost 注册

```kotlin
// Tab 页
composable(Tab.Record.route) {
    RecordScreen(onNavigateToEdit = { navController.navigate(Routes.sampleEdit(it)) })
}

// 带参页
composable(
    route = Routes.SAMPLE_EDIT_WITH_ID,
    arguments = listOf(navArgument("sampleId") {
        type = NavType.LongType
        defaultValue = -1L
    })
) { backStackEntry ->
    val id = backStackEntry.arguments?.getLong("sampleId") ?: -1L
    SampleEditScreen(
        sampleId = id.takeIf { it > 0 },
        onBack = { navController.popBackStack() }
    )
}
```

`defaultValue = -1L` + `takeIf { it > 0 }` 区分新建/编辑，是约定。

### 9.4 Tab 切换 popUpTo 逻辑

底部栏 onClick 标准三件套：

```kotlin
navController.navigate(route) {
    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```

保证 Tab 间切换保留各自状态、不重复入栈。

### 9.5 Screen 不持 NavController

Screen 的可导航 Composable 只接收回调 lambda（`onOpenEdit: (Long) -> Unit`、`onBack: () -> Unit`），实际 `navController.navigate(...)` 全部写在 NavHost 的 `composable {}` 块内。Screen 自身不持有 NavController，保证可独立 Preview 与单测。

---

## 10. ViewModel 与状态管理

### 10.1 内嵌 Factory 模式

每个 ViewModel 内嵌一个 `class Factory : ViewModelProvider.Factory`，构造参数与 VM 的 Repository 参数对齐：

```kotlin
class XxxViewModel(
    private val repository: XxxRepository
) : ViewModel() {
    val uiState: StateFlow<XxxUiState> = repository.observeAll()
        .map { XxxUiState(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, XxxUiState())

    class Factory(private val repository: XxxRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = XxxViewModel(repository) as T
    }
}
```

带业务参数的 Factory（如编辑页）把 `id: Long?` 也作为构造参数，Screen 从 NavBackStackEntry 取出 id 后传给 Factory。

### 10.2 状态约定

- 状态统一用 `StateFlow<UiState>` + `combine(...).stateIn(viewModelScope, SharingStarted.Eagerly, initialValue)`。
- 一次性事件用 `MutableSharedFlow` + `extraBufferCapacity = 1`。
- `combine` 超 5 路时先两两 combine 成 Pair 再并入主 combine（绕过 `combine` 5 参上限）。

### 10.3 Screen 拆分约定

- 顶层可导航 `XxxScreen`（取 container + 建 VM + 转发回调）
- 私有 `XxxContent`（纯状态驱动，便于 Preview）
- 私有子组件 + `@Preview`

---

## 11. 数据导出框架

### 11.1 数据结构

```kotlin
@Serializable
data class ExportMeta(
    val version: Int = 1,
    val exportedAt: Long
)

@Serializable
data class ExportPayload(
    val domains: List<Domain> = emptyList(),
    // ...每张表一个字段
    val meta: ExportMeta
)
```

- 实体类本身带 `@Serializable`（Room 实体注解与序列化注解并存）。
- 枚举由 kotlinx.serialization 自动按名字编解码。
- `version` 写死在 meta，`exportedAt` 用 `now()` 注入，为后续导入迁移预留。

### 11.2 导出器：interface + Impl 双层

```kotlin
interface DataExporter {
    suspend fun export(): String
}

class DataExporterImpl(
    private val xxxRepository: XxxRepository,
    // ...注入全部 Repository
    private val now: () -> Long = TimeUtil::nowMillis   // 测试可换 fixedNow
) : DataExporter {
    override suspend fun export(): String {
        val payload = ExportPayload(
            xxxs = xxxRepository.observeAll().first(),
            // ...每个 Repository 调 observe().first() 取首帧快照
            meta = ExportMeta(version = 1, exportedAt = now())
        )
        return Json { prettyPrint = true; encodeDefaults = true }
            .encodeToString(ExportPayload.serializer(), payload)
    }
}
```

抽 interface 的目的：让 ViewModel 能注入桩实现，单测完全不碰 Room。

### 11.3 调用方：状态机 + SAF 写文件分离

- ViewModel 只负责 `export()` 拿 JSON 字符串 → emit `Ready(json)`。
- 真正写 Uri 由 Screen 通过 SAF `CreateDocument` launcher 完成，写完回调 `onWritten/onFailed`。
- 状态用 `sealed interface ExportState`（Idle/Exporting/Ready/Success/Failed），挂 `StateFlow`（支持配置变更恢复"等待写入"态）。
- `export()` 内置防重复点击。

### 11.4 可复用性

**可直接保留**：`ExportMeta`/`ExportPayload` 双 data class 结构、`DataExporter` interface + Impl 分层、注入 `now` lambda、Json 配置、`ExportState` 状态机 + SAF 分离架构。

**需改**：`ExportPayload` 字段列表、`DataExporterImpl` 注入的 Repository 集合与取数查询、实体补 `@Serializable`。

---

## 12. 单元测试范式

### 12.1 目录组织

测试根 `app/src/test/java/com/example/app/`，**完全镜像 main 包结构**。

### 12.2 测试依赖

`junit` + `robolectric` + `androidx.room.testing` + `androidx.test.core` + `kotlinx.coroutines.test` + `kotlinx.coroutines.android`。

**没有用** Turbine / Truth / MockK；断言全用 JUnit `Assert.*`，Flow 验证靠 `.first()` / `advanceUntilIdle()`。

`testOptions { unitTests { includeAndroidResources = true } }`——Robolectric 必需。

### 12.3 ViewModel 测试标准写法（无 Robolectric，纯 JVM）

1. `StandardTestDispatcher` 实例，`@Before` 里 `Dispatchers.setMain(testDispatcher)`，`@After` 里 `resetMain()`。
2. `runTest(testDispatcher)` 跑用例，关键异步点后 `advanceUntilIdle()` 推进。
3. **不用 mock 库，造 fake DAO**：实现 `XxxDao` 接口，内部用 `MutableStateFlow<List<Entity>>` + `update{}` 驱动，`observe*` 返回 `all.map{...}` 派生 Flow。未用到的方法 `throw NotImplementedError()` 占位。
4. fake DAO 喂给**真实 Repository**，再喂给被测 ViewModel——Repository 的真实逻辑也被覆盖。
5. 验证 StateFlow：`vm.uiState.value` 直接断言字段（`advanceUntilIdle` 后状态已稳定）。
6. 验证一次性事件（Channel/SharedFlow）：`launch { vm.events.toList(collected) }` 收集，`job.cancel()` 后断言 `collected.contains(...)`。
7. 时间可控：ViewModel 构造注入 `now = { fixedNow }`。

### 12.4 Room 测试：in-memory database

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppDatabaseTest {
    private lateinit var db: AppDatabase

    @Before fun setup() {
        db = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext())
    }
    @After fun teardown() { db.close() }

    @Test fun `xxx`() = runTest {
        // 用 flow.first() 读数据
    }
}
```

`createInMemory` 工厂定义在 main 源码里（`allowMainThreadQueries` + 复用 `SeedCallback`）。覆盖 TypeConverter 往返、种子数据、各 JOIN/聚合查询、CRUD。

### 12.5 导出测试

复用 in-memory DB + 真 Repository，export → decode → 断言全表一致性 + meta + 空库不崩。是"端到端集成式单测"。

### 12.6 测试覆盖层

- 数据层：DB（in-memory）、DataStore（Robolectric 走真文件系统）、Repository、Exporter（集成式）
- UI 状态层：各 ViewModel 测试（fake DAO + 真 Repository + StandardTestDispatcher）
- **未覆盖** UI Compose 渲染层（无 Compose UI 测试 / screenshot 测试）

---

## 13. 新仓库落地顺序

1. **骨架**：建 `Application` + `AppContainer` + 空 `AppRoot`（单 Tab 跑通 Compose）。
2. **Room 跑通**：加一个 entity / dao / relation / database，跑通建库 + seed。
3. **一个 feature 全链路**：加 Screen + ViewModel + Factory，跑通"取 container → 注入 → StateFlow → collectAsStateWithLifecycle"。
4. **带参编辑路由**：加第二个 Tab 与带参编辑页，验证 `popUpTo` + `takeIf{it>0}` 范式。
5. **主题与组件**：拷 `theme/` + `components/`，换成自己的设计语言。
6. **测试**：先加一个 ViewModel 测试（fake DAO 套路）+ 一个 DB 测试（in-memory），跑通范式后批量铺开。
7. **导出**（可选）：加 `ExportPayload` + `DataExporter` + `ExportState` 状态机。
8. **签名发布**：生成 keystore，配 `keystore.properties`（容错读取），`assembleRelease` 打包，GitHub Release 分发。

**骨架精髓**（五点原样照搬）：
1. 零注解框架
2. Application 持容器
3. Screen 取容器建 VM
4. Routes object 集中管路由
5. Screen 不持 NavController

---

## 14. 复用清单速查

### ✅ 可直接复制（改包名/表名/色值即可）

| 类别 | 文件/范式 |
|---|---|
| 构建配置 | `libs.versions.toml`、项目级 `build.gradle`、`settings.gradle`、`gradle.properties`、`gradle-wrapper.properties`（**补齐 gradlew**）、`.gitignore` |
| 签名 | `keystore.properties` 容错读取逻辑（**重新生成密钥**） |
| ProGuard | kotlinx-serialization 段 + 枚举段 |
| Manifest | 骨架（无权限 + 单 launcher Activity） |
| DI | `Application` 持 `AppContainer`（`by lazy`） + Screen 取 container + VM 内嵌 `Factory` |
| 数据层 | Database 工厂（`create`/`createInMemory`）、Converters（枚举存 name）、DAO 返回值约定、JOIN + `@Embedded` 关系类、Repository 薄包装、实体范式（Long 主键 + Long 时间戳 + `@Serializable`） |
| 迁移 | `fallbackToDestructiveMigration` + `exportSchema=true` 起步 |
| 种子 | `Callback.onCreate` 内同步 `execSQL("INSERT OR IGNORE ...")` |
| UI | `theme/` 结构、`Type.kt` 双族字体思路 + `font_certs.xml`、`components/` 组件库（改设计语言）、页面外壳范式、列表行 + 分割线范式 |
| 导航 | `Tab` sealed class + `Routes` object + `NavHost` 注册 + `popUpTo` 三件套 + Screen 不持 NavController |
| ViewModel | `StateFlow<UiState>` + `combine.stateIn` + `SharedFlow` 事件 + 内嵌 `Factory` |
| 导出 | `ExportMeta`/`ExportPayload` 双 data class + `DataExporter` interface/Impl + `ExportState` 状态机 + SAF 分离 |
| 测试 | `includeAndroidResources`、fake DAO（MutableStateFlow 驱动）喂真 Repository、`StandardTestDispatcher` + `runTest` + `advanceUntilIdle`、in-memory DB 工厂、导出集成测试 |

### ❌ GrowthOS 业务专属（换仓库需替换）

- 具体实体集（Domain/ErrorType/Sample/Training/Principle/Knowledge）及字段、外键、索引
- 种子数据（ErrorTypeSeed 的 8 个错误类型）
- 撞名合并等业务规则（ErrorTypeRepository）
- domain model 枚举（Attribution/TrainingStatus/KnowledgeType）
- `SelectedDomainStore`（"当前选中领域"偏好，结构可复用但 key 名要换）
- 具体配色语义（琥珀 = "向前看"）、`LedgerShapes` 全直角、账本风格
- 具体 Tab（记录/领域/复盘）、`Routes` 里的具体路由与参数名
- 各 `ui/<feature>` 页面与业务交互
- `ExportPayload` 具体六表字段、`DataExporterImpl` 注入的具体 Repository
- `TimeUtil` 的具体窗口计算（模式可复用，实现偏业务）
- `applicationId`/`namespace`/`rootProject.name`/`MainActivity`/`Theme.GrowthOS` 等命名

---

> **一句话总结**：构建配置、手动 DI、Room 工程结构、导航范式、ViewModel 状态约定、测试套路是纯模板；具体实体表、种子数据、配色字体、业务页面、导出字段是 GrowthOS 业务专属。开新仓库时按第 13 节顺序落地，五点骨架精髓原样照搬即可。
