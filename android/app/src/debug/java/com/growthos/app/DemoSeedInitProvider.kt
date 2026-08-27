package com.growthos.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * debug-only 初始化器:ContentProvider 在 Application.onCreate 之前自动调用,
 * 借此注册演示种子动作(release 构建无此文件,SeedHook.seedAction 保持 null)。
 */
class DemoSeedInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        DemoSeed.register()
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
