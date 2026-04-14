package com.hstc.quicklogin

import android.app.Application
import com.hstc.quicklogin.di.AppContainer

class HstcQuickLoginApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
