package com.growthos.app.data.local.relation

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.growthos.app.data.local.entity.PracticeSession

/**
 * 练习记录 + 领域名(列表展示用,LEFT JOIN 容错——领域被隐藏后列表仍显示,设计 D12)。
 */
data class PracticeSessionWithDomainName(
    @Embedded val session: PracticeSession,
    @ColumnInfo(name = "domainName") val domainName: String?
)
