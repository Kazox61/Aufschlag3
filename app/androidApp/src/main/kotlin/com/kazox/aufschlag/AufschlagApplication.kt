package com.kazox.aufschlag

import android.app.Application
import com.kazox.aufschlag.app.di.initKoin
import org.koin.android.ext.koin.androidContext

class AufschlagApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        initKoin {
            androidContext(this@AufschlagApplication)
        }
    }
}
