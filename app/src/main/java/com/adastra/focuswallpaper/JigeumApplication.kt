package com.adastra.focuswallpaper

import android.app.Application

class JigeumApplication : Application() {
    private var mainActivityStarted = false
    fun consumeFirstMainActivity(): Boolean {
        val first = !mainActivityStarted
        mainActivityStarted = true
        return first
    }
    override fun onCreate() {
        super.onCreate()
        // Recover interrupted imports before any screen or timer receiver reads data.
        BackupImporter.recover(this)
    }
}
