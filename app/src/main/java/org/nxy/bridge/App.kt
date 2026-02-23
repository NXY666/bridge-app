package org.nxy.bridge

import android.app.Application
import android.content.Context

class App : Application() {
    companion object {
        private var _appContext: Context? = null

        private var _appInstance: Application? = null

        val context: Context
            get() = _appContext
                ?: throw IllegalStateException("AppContext accessed before Application.onCreate()")

        val instance: Application
            get() = _appInstance
                ?: throw IllegalStateException("AppInstance accessed before Application.onCreate()")
    }

    override fun onCreate() {
        super.onCreate()

        _appContext = applicationContext

        _appInstance = this
    }
}