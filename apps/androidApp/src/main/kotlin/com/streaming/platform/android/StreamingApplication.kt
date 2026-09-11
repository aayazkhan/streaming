package com.streaming.platform.android

import android.app.Application
import com.streaming.platform.android.di.AppContainer

class StreamingApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
