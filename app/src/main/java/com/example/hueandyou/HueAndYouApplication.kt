package com.example.hueandyou

import android.app.Application
import com.example.hueandyou.di.AppContainer
import com.example.hueandyou.di.DefaultAppContainer

class HueAndYouApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(applicationContext)
    }
}
