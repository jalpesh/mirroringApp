package com.example.mirroringapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application class with Hilt dependency injection.
 * Initializes Timber logging for debug builds.
 */
@HiltAndroidApp
class MirroringApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.i("MirroringApplication initialized")
    }
}
