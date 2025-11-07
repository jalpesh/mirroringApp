package com.example.mirroringapp

import android.app.Application
import com.example.mirroringapp.util.PersistentLogger
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
        
        // Initialize persistent logger
        PersistentLogger.initialize(this)
        
        Timber.i("MirroringApplication started")
        PersistentLogger.i("Application initialized successfully")
    }
}
