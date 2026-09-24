package com.example.hueandyou

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import com.example.hueandyou.di.AppContainer
import com.example.hueandyou.di.DefaultAppContainer

class HueAndYouApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build(),
            )
        }
        container = DefaultAppContainer(applicationContext)
    }
}
