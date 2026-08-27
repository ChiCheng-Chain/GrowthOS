package com.growthos.app

import android.app.Application
import com.growthos.app.di.AppContainer

class GrowthOSApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SeedHook.seedAction?.invoke(this)
    }
}
