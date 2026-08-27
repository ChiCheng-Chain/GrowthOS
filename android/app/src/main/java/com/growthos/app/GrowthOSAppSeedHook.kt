package com.growthos.app

/**
 * 演示数据钩子槽位:main 声明为可空 var,默认 null(生产不灌数据)。
 * debug sourceSet 在 Application.onCreate 前通过 app 初始化赋值——
 * 用独立的 object 注册,避免 main/debug 同名函数冲突。
 */
object SeedHook {
    /** debug 构建注入的种子动作;release 恒为 null。 */
    var seedAction: ((GrowthOSApp) -> Unit)? = null
}
