# GrowthOS Android App

GrowthOS 第一版 MVP 的 Android 实现。技术方案与需求文档见 [docs/](../docs/) 目录。

> 本工程是 MVP 骨架:Gradle 配置齐了、4 个核心页面有了 Compose Preview 占位 UI,但**数据层(Room)、ViewModel、Repository 都没接**。下一步工作是按 [技术方案设计.md §11 开发顺序建议](../docs/技术方案设计.md) 推进。

## 项目结构

```
android/
├── app/
│   ├── build.gradle.kts         # 模块构建配置
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/growthos/app/
│       │   ├── GrowthOSApp.kt           # Application
│       │   ├── MainActivity.kt          # 唯一 Activity
│       │   ├── domain/model/            # 占位数据(预览用,后续删除)
│       │   └── ui/
│       │       ├── theme/               # 主题与字号
│       │       ├── navigation/          # 路由 + 底部导航 + Preview
│       │       ├── record/              # 今日记录页
│       │       ├── domain_view/         # 领域页
│       │       └── weekly/              # 周复盘页
│       └── res/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradle/
    ├── libs.versions.toml      # 所有依赖版本集中在这
    └── wrapper/
```

## 在 Android Studio 中打开

1. 打开 Android Studio(已装在 C 盘,版本 2025.1.1.13 Narwhal)。
2. Welcome 界面点 **Open**,选 `D:\GrowthOS\android` 整个目录,**不是**选 `app` 子目录。
3. Studio 会开始第一次 Gradle Sync,这会下载 Gradle 8.10.2、AGP、Kotlin、Compose BOM 等依赖。`settings.gradle.kts` 里已经配置了阿里云镜像,国内网络应该不会卡。
4. 第一次 Sync 可能要 3~10 分钟。底部进度条走完即可。

## 怎么看到 4 个核心页面的样子(Compose Preview)

**最快路径,不需要跑模拟器,不需要连手机。**

1. 项目树左侧展开 `app/src/main/java/com/growthos/app/ui/navigation/`,打开 `GrowthOSApp.kt`。
2. 文件里有 4 个 `@Preview` 函数:
   - `GrowthOSRootPreview` — 整个 App(底部导航 + 首页)
   - `RecordScreenPreview` — 今日记录页
   - `DomainScreenPreview` — 领域页
   - `WeeklyScreenPreview` — 周复盘页
3. 点击每个 `@Preview` 函数上面的 **Split / Design** 按钮(代码编辑器右上角),Studio 右侧会渲染出该页面的样子。
4. 想看其他页面?展开 `app/src/main/java/com/growthos/app/ui/record/`、`domain_view/`、`weekly/`,每个 `XxxScreen.kt` 文件暂时还没独立的 Preview(我先写在导航文件里集中预览);后续每加一个页面/组件,会在该文件里加一个 `@Preview` 方便你看。

## 怎么跑起来(可选,需要模拟器或真机)

> 这一步是验证闭环的"启动 App"层面。MVP 骨架没有数据层,所以跑起来只是一个空 UI,跟 Preview 看到的几乎一样。真正能用的版本要等数据层接好。

- **真机**:你 Android 16 手机开"开发者选项 → USB 调试",用 USB 连电脑,Studio 顶部设备栏选你的手机,点绿色三角 Run。
- **模拟器**:Tools → Device Manager → Create Device,选 Pixel 系列 + Android 16 系统镜像,创建后启动,然后 Run。

## 版本号说明

| 组件 | 版本 |
|---|---|
| Android Studio | 2025.1.1.13 Narwhal |
| 内嵌 JDK | 21.0.6 |
| Gradle | 8.10.2 |
| AGP | 8.7.3 |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 36 (Android 16) |
| minSdk | 26 (Android 8.0) |
| Build-Tools | 37.0.0(SDK 已装) |
| Compose BOM | 2024.12.01 |
| Room | 2.6.1 |
| Navigation Compose | 2.8.5 |

如果 Sync 报版本不兼容(尤其是 AGP ↔ Studio、AGP ↔ Kotlin、Compose ↔ Kotlin),所有版本号集中在 `gradle/libs.versions.toml`,改完再 Sync 即可。

## 下一步

按 [技术方案设计.md §11 开发顺序建议](../docs/技术方案设计.md) 推进。第 2 步是建数据层(Entity/DAO/Database/Repository + 单测),这是从"只能看 UI"到"能录样本"的关键一步,大概要 1~2 天工作量。
