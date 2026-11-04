package io.github.xiaowu20245.nekopaint

import android.app.Application
import io.github.xiaowu20245.nekopaint.di.AppContainer

class NekoPaintApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
