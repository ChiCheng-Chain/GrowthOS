package com.growthos.app.domain.model

/**
 * 关键因素极性(feature 2026-09-16 / 设计 D1)。
 *
 * 负向=错误(失败样本语义),正向=做对的事(成功样本语义)。
 * 样本成败不显式存储,由所选因素的极性推导(BR-2)。
 * 与 Attribution 等同构:Room TypeConverter 存 [name],未知值抛错拒绝脏数据。
 */
enum class Polarity {
    NEGATIVE,
    POSITIVE
}
