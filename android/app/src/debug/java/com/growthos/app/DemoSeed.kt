package com.growthos.app

import android.content.Context
import com.growthos.app.data.local.entity.Knowledge
import com.growthos.app.data.local.entity.Principle
import com.growthos.app.data.local.entity.Sample
import com.growthos.app.data.local.entity.Training
import com.growthos.app.domain.model.Attribution
import com.growthos.app.domain.model.KnowledgeType
import com.growthos.app.domain.model.TrainingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 演示数据种子(仅 debug 构建;release sourceSet 无此文件,SeedHook.seedAction 恒为 null)。
 *
 * 空库时插入一批跨 30 天的逼真数据:3 领域 × 6 错误类型 × 14 样本 × 3 训练项 × 3 原则 × 3 知识,
 * 让领域页/周复盘/筛选弹层在演示时全部有真实内容。SharedPreferences 标记位保证只灌一次。
 */
object DemoSeed {

    private const val PREFS = "demo_seed_prefs"
    private const val KEY_SEEDED = "seeded"

    /** debug Application 类在 onCreate 时调用此注册;返回后 GrowthOSApp 主动触发一次。 */
    fun register() {
        SeedHook.seedAction = { app -> seedIfEmpty(app) }
    }

    private fun seedIfEmpty(app: GrowthOSApp) {
        seedInternal(app)
    }

    private fun seedInternal(app: GrowthOSApp) {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()

        val container = app.container
        val day = 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        fun daysAgo(n: Double) = (now - (n * day)).toLong()

        CoroutineScope(Dispatchers.IO).launch {
            val domainRepo = container.domainRepository
            val errorRepo = container.errorTypeRepository
            val sampleRepo = container.sampleRepository
            val trainingRepo = container.trainingRepository
            val principleRepo = container.principleRepository
            val knowledgeRepo = container.knowledgeRepository

            // 领域
            val coding = domainRepo.create("编程")
            val chess = domainRepo.create("酒馆战旗")
            val boxing = domainRepo.create("拳击")

            // 错误类型(种子 8 个已在 onCreate 首启建库时插入,直接按名取 id)
            val allTypes = errorRepo.observeAll().first()
            fun typeId(name: String): Long = allTypes.first { it.name == name }.id
            val boundary = typeId("边界条件遗漏")
            val rush = typeId("信息不足就行动")
            val judge = typeId("对局面判断错误")
            val greed = typeId("贪收益导致下限崩盘")
            val exec = typeId("执行变形")
            val tilt = typeId("压力下急躁")

            // 样本:编程 6 / 战旗 5 / 拳击 3 老数据 + 加密批(跨 0~29 天,让预览收敛/查看全部/筛选有戏可看)
            data class Row(val d: Long, val e: Long, val a: Attribution, val emo: Int?, val r: String, val rev: String)
            val rows = listOf(
                Row(coding, boundary, Attribution.CONTROLLABLE, 4, "退款金额为 0 的分支直接崩了,影响 12 笔订单", "金额为 0 的分支要先进状态表"),
                Row(coding, rush, Attribution.CONTROLLABLE, 3, "没确认接口版本就动手,联调白跑一下午", "先读文档再写代码"),
                Row(coding, judge, Attribution.ENVIRONMENT, null, "线上偶现超时,定位错了方向排查了 3 小时", "先看监控大盘再猜"),
                Row(coding, boundary, Attribution.CONTROLLABLE, 2, "优惠券叠加边界漏了,多发了 5 张券", "边界用例先写后写代码"),
                Row(coding, exec, Attribution.UNCONTROLLABLE, null, "重构进行到一半被紧急需求打断,回滚", "重构前先切分支"),
                Row(coding, tilt, Attribution.CONTROLLABLE, 5, "发布前夜临时改配置,踩了缓存坑", "发布窗口不动配置"),
                Row(chess, greed, Attribution.CONTROLLABLE, 4, "锁血局贪吃 3 个随从被翻盘,第 7 出局", "锁血先保下限"),
                Row(chess, judge, Attribution.CONTROLLABLE, 3, "高血量错判对手阵容,后期完全打不过", "先数对面粉数量"),
                Row(chess, tilt, Attribution.CONTROLLABLE, 4, "连败后上头抢节奏,3 回合出局", "连败先停一手"),
                Row(chess, rush, Attribution.UNCONTROLLABLE, null, "没看本场 banned 英雄,关键牌被卡", "开局先看 ban 位"),
                Row(chess, greed, Attribution.CONTROLLABLE, 2, "经济领先时乱花,决赛圈差 1 块钱", "决赛前留 2 金保底"),
                Row(boxing, exec, Attribution.CONTROLLABLE, 4, "实战中直拳变形,被连续反击命中", "每周固定打靶 200 次"),
                Row(boxing, tilt, Attribution.CONTROLLABLE, 3, "被压到围角就乱抡,体力掉光", "贴墙先抱缠"),
                Row(boxing, exec, Attribution.ENVIRONMENT, null, "新手局节奏不适应,回合间休息不够", "赛前跳绳练 3 轮"),
                // —— 加密批:编程 9 条(触发预览 5+全部入口) ——
                Row(coding, boundary, Attribution.CONTROLLABLE, 3, "分页拉取漏了空页判断,列表尾部重复 20 条", "分页终止条件先进用例"),
                Row(coding, rush, Attribution.OPPONENT_EXTERNAL, 2, "依赖方临时改协议没通知,联调又白跑", "关键依赖每天对一次口径"),
                Row(coding, boundary, Attribution.CONTROLLABLE, 4, "时区转换漏了夏令时,海外用户日历偏 1 小时", "时间用例覆盖 UTC+8 外时区"),
                Row(coding, tilt, Attribution.CONTROLLABLE, 5, "评审被质疑当场急了,语气失控", "被质疑先复述对方问题"),
                Row(coding, judge, Attribution.CONTROLLABLE, 3, "低估了数据量,方案上线三天就撑不住", "方案前先算量级"),
                Row(coding, exec, Attribution.UNCONTROLLABLE, null, "感冒状态差,code review 漏了明显问题", "状态差只做低风险改动"),
                Row(coding, boundary, Attribution.CONTROLLABLE, 2, "并发下计数器没加锁,统计少了 30%", "共享可变状态先想并发"),
                Row(coding, rush, Attribution.CONTROLLABLE, 3, "没跑全量测试就发布,回归挂了两条", "发布前 CI 必须绿"),
                Row(coding, judge, Attribution.ENVIRONMENT, null, "第三方 SDK 升级破坏兼容,夜间崩溃上涨", "升级先看 changelog"),
                // —— 加密批:战旗 6 条 ——
                Row(chess, greed, Attribution.CONTROLLABLE, 4, "优势局追三连鸡,贪节奏被第五抬走", "优势先稳排名"),
                Row(chess, judge, Attribution.CONTROLLABLE, 2, "错估酒馆刷新概率,白丢 5 金", "刷新期望先背熟"),
                Row(chess, tilt, Attribution.CONTROLLABLE, 5, "被连胜嘲讽后上头换阵容,直接第八", "关掉对面表情"),
                Row(chess, greed, Attribution.CONTROLLABLE, 3, "决赛圈舍不得卖核心卡,差一步吃鸡", "阵容服务于名次"),
                Row(chess, rush, Attribution.UNCONTROLLABLE, null, "排队期间没想好开局路线,前 5 回合乱拿", "排队时定开局"),
                Row(chess, judge, Attribution.OPPONENT_EXTERNAL, null, "决赛对手天胡开局,非战之罪", "非战之罪不复盘"),
                // —— 加密批:拳击 5 条 ——
                Row(boxing, exec, Attribution.CONTROLLABLE, 4, "刺拳距离感又丢了,整场够不着人", "每周加 2 轮距离感步法"),
                Row(boxing, tilt, Attribution.CONTROLLABLE, 4, "挨了一记重拳后想立刻还手,露了破绽", "挨拳先抱缠一回合"),
                Row(boxing, exec, Attribution.CONTROLLABLE, 2, "组合拳第三下习惯性收手,打不出连击", "空击每天 3 组×10"),
                Row(boxing, judge, Attribution.CONTROLLABLE, 3, "实战全程没观察对手习惯,被动挨打", "第一回合只做观察"),
                Row(boxing, tilt, Attribution.ENVIRONMENT, null, "护具不合适磨破眉骨,训练中断", "自备护具")
            )
            rows.forEachIndexed { i, row ->
                sampleRepo.insert(
                    Sample(
                        domainId = row.d,
                        recordedAt = daysAgo(if (i < 6) (i * 4.5) else (12 + (i - 6) * 3.2)),
                        result = row.r, errorTypeId = row.e, attribution = row.a,
                        emotionIntensity = row.emo, review = row.rev
                    )
                )
            }

            // 训练项:2 个进行中 + 1 个已完成
            trainingRepo.create(
                Training(
                    domainId = coding, errorTypeId = boundary, goal = "两周内写出全部边界用例再提交",
                    acceptanceCriteria = "每次 PR 附边界 checklist", startedAt = daysAgo(10.0),
                    endedAt = null, status = TrainingStatus.IN_PROGRESS, note = null
                )
            )
            trainingRepo.create(
                Training(
                    domainId = chess, errorTypeId = greed, goal = "锁血局固定保下限策略",
                    acceptanceCriteria = "连续 10 局锁血不死", startedAt = daysAgo(6.0),
                    endedAt = null, status = TrainingStatus.IN_PROGRESS, note = null
                )
            )
            trainingRepo.create(
                Training(
                    domainId = coding, errorTypeId = rush, goal = "动手前读完整篇接口文档",
                    acceptanceCriteria = "联调一次通过", startedAt = daysAgo(20.0),
                    endedAt = daysAgo(8.0), status = TrainingStatus.COMPLETED, note = "已完成两周"
                )
            )
            // 加密批:拳击 1 个进行中(让该领域有训练卡内容) + 编程 1 个已放弃(三状态齐)
            trainingRepo.create(
                Training(
                    domainId = boxing, errorTypeId = tilt, goal = "挨拳后先抱缠一回合再组织反击",
                    acceptanceCriteria = "实战连续 3 场不出现上头换血", startedAt = daysAgo(4.0),
                    endedAt = null, status = TrainingStatus.IN_PROGRESS, note = null
                )
            )
            trainingRepo.create(
                Training(
                    domainId = coding, errorTypeId = judge, goal = "所有方案先算数据量级再评审",
                    acceptanceCriteria = "方案文档附量级估算", startedAt = daysAgo(26.0),
                    endedAt = daysAgo(15.0), status = TrainingStatus.ABANDONED, note = "被流程性工具替代"
                )
            )

            // 原则(共 8 条:编程 4 / 战旗 2 / 拳击 2,触发预览 3+全部入口)
            principleRepo.insert(
                Principle(content = "边界先列清单再动手——枚举比回忆可靠", createdAt = daysAgo(9.0), domainId = coding, errorTypeId = boundary)
            )
            principleRepo.insert(
                Principle(content = "下限比上限重要:先想怎么不死,再想怎么赢", createdAt = daysAgo(5.0), domainId = chess, errorTypeId = greed)
            )
            principleRepo.insert(
                Principle(content = "情绪上头时做的决定几乎全是错的,先暂停 10 秒", createdAt = daysAgo(3.0), domainId = boxing, errorTypeId = tilt)
            )
            principleRepo.insert(
                Principle(content = "动手之前先读完整篇文档,半知半解写出来全是坑", createdAt = daysAgo(7.5), domainId = coding, errorTypeId = rush)
            )
            principleRepo.insert(
                Principle(content = "被质疑时先复述对方的问题,而不是急着辩解", createdAt = daysAgo(6.0), domainId = coding, errorTypeId = tilt)
            )
            principleRepo.insert(
                Principle(content = "优势局只做不亏的决策,排名优先于吃鸡", createdAt = daysAgo(4.0), domainId = chess, errorTypeId = greed)
            )
            principleRepo.insert(
                Principle(content = "第一回合只观察不出力,信息比先手值钱", createdAt = daysAgo(2.5), domainId = boxing, errorTypeId = judge)
            )
            principleRepo.insert(
                Principle(content = "共享可变状态先想并发,加锁或改不可变", createdAt = daysAgo(1.5), domainId = coding, errorTypeId = boundary)
            )

            // 知识(外部摄取,共 9 条:编程 4 / 战旗 3 / 拳击 2,触发预览 3+全部入口)
            knowledgeRepo.insert(
                Knowledge(content = "《清单革命》:复杂任务先建 checklist 再执行", type = KnowledgeType.EXPERIENCE, createdAt = daysAgo(7.0), domainId = coding)
            )
            knowledgeRepo.insert(
                Knowledge(content = "把战旗 ban/pick 阶段的常用套路整理成小抄", type = KnowledgeType.TODO, createdAt = daysAgo(4.0), domainId = chess)
            )
            knowledgeRepo.insert(
                Knowledge(content = "看一期拳王防守反击的技术拆解视频", type = KnowledgeType.TODO, createdAt = daysAgo(2.0), domainId = boxing)
            )
            knowledgeRepo.insert(
                Knowledge(content = "《系统化思维》:先画因果回路再下结论", type = KnowledgeType.EXPERIENCE, createdAt = daysAgo(6.0), domainId = coding)
            )
            knowledgeRepo.insert(
                Knowledge(content = "整理时区/夏令时测试用例模板,沉淀给组内", type = KnowledgeType.TODO, createdAt = daysAgo(5.0), domainId = coding)
            )
            knowledgeRepo.insert(
                Knowledge(content = "酒馆更新公告:酒馆刷新概率上调 2%,打法要变", type = KnowledgeType.EXPERIENCE, createdAt = daysAgo(3.5), domainId = chess)
            )
            knowledgeRepo.insert(
                Knowledge(content = "找一部职业比赛第一视角,观察对手习惯的观察点", type = KnowledgeType.TODO, createdAt = daysAgo(3.0), domainId = chess)
            )
            knowledgeRepo.insert(
                Knowledge(content = "《丹·哈迪拳击教学》:距离感的步法练习章节", type = KnowledgeType.EXPERIENCE, createdAt = daysAgo(2.5), domainId = boxing)
            )
            knowledgeRepo.insert(
                Knowledge(content = "约一次轻实战,专门练第一回合纯观察", type = KnowledgeType.TODO, createdAt = daysAgo(1.0), domainId = boxing)
            )
        }
    }
}
