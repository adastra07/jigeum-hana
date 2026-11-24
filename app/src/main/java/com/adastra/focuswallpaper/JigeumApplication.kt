package com.adastra.focuswallpaper

import android.app.Application

class JigeumApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Recover interrupted imports before any screen or timer receiver reads data.
        BackupImporter.recover(this)
    }
}
