# AGENTS.md

## Rule levels

- `[Required]`: must be followed unless a documented project exception applies.
- `[Default]`: follow unless the task records a concrete reason to differ.
- `[Reference]`: navigation or context, not a completion gate.

## Execution discipline `[Required]`

- Read the nearest applicable instructions, relevant code, configuration, architecture knowledge, business baseline, and feature documents before changing files.
- Keep changes within the user's requested scope; preserve unrelated and unexplained modifications.
- Treat secrets, production data, deployment, remote writes, database mutations, and external notifications as separately authorized operations.
- Verify changed behaviour with proportionate tests or checks and record unverified scope.

## Feature documentation `[Required]`

- Store work under `docs/features/<module_id>/<feature_id>/`.
- Maintain `requirement.md`, `design.md`, and `verification.md` for each feature; concise content is acceptable, missing material facts are not.
- Before implementation, reconcile requirement, design, architecture knowledge, and business baseline.
- After implementation and verification, run delivery reconciliation so every affected feature document, architecture authority, business baseline, index, repository instruction, and operational reference is updated or explicitly verified current.

## Architecture knowledge `[Required]`

- Use `docs/architecture/README.md` as the code-navigation index.
- Maintain knowledge at module, interface, ownership, dependency, and representative-chain granularity.
- Keep target plans distinct from current and verified facts.

## Long-task handoff `[Required]`

- Store transient cross-session handoffs under `.ai-work/handoffs/<work-identity>/`, never scattered through source or repository-root files.
- Use `current.md` as the stable resume path and verify it is locally ignored before capture.
- A resumed session rereads current instructions and repository state; a handoff does not grant new permissions or replace project Authorities.

## Project-specific facts

- Project purpose: GrowthOS——个人成长复盘与刻意训练系统（记录失误 → 复盘归因 → 建训练项 → 沉淀原则闭环）。已完成 MVP，全部数据本机 Room，无网络无账号。
- Primary users: 产品所有者本人（单用户）。
- Technology authority files: `docs/技术方案设计.md`（选型权威）、`android/gradle/libs.versions.toml`（版本集中管理）。
- Standard commands: 见下方「构建与测试」。工作目录 `android/`。
- High-risk areas:
  - `data/local/GrowthOSDatabase.kt`——`fallbackToDestructiveMigration`，schema 变更即清库（OD-001）。
  - 外键与删除保护——Sample/Training 外键无 CASCADE，删除前置引用检查是数据安全不变量（CE-001）。
  - DataStore 进程级唯一——只能在 `AppContainer` 构造，不得在 Composable/Factory 内创建。
  - 聚合 SQL 的统计口径——NULL 情绪计入分母、半开区间窗口（CE-002、AM-002）。
- Build & test:
  - 本机无 `gradlew`，用缓存二进制: `"C:\Users\%USERNAME%\.gradle\wrapper\dists\gradle-8.10.2-bin\...\gradle-8.10.2\bin\gradle" -p android :app:assembleRelease`，JAVA_HOME 指向 Android Studio JBR 21。
  - 测试: `gradle -p android :app:testDebugUnitTest`（17 个测试类，全部通过为合入前提）。
- Local handoff root: `.ai-work/handoffs/`
- Document index: [`docs/README.md`](docs/README.md)

## Repository conventions `[Default]`

- 提交信息格式沿用 git log 现状: `type(scope): 中文描述`（feat/fix/chore/docs）。
- 注释与文档以中文为主，代码标识符英文。
- UI 改动保持「账本式」设计语言：发丝线分隔、无阴影 Card、Mono 字体标签。
- 每阶段开发遵循「需求 → 设计 → 实现 → 验证」文档先行，历史阶段文档在 `docs/阶段*-*.md`。
