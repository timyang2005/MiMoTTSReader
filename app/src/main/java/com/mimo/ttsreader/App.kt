package com.mimo.ttsreader

import android.app.Application
import com.mimo.ttsreader.util.ConfigManager
import com.mimo.ttsreader.model.VoiceConfig

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        val config = ConfigManager.getConfig(this)
        if (config.autoStart) {
            startTtsService()
        }
    }

    private fun startTtsService() {
        val intent = android.content.Intent(this, com.mimo.ttsreader.service.TtsService::class.java).apply {
            action = com.mimo.ttsreader.service.TtsService.ACTION_START
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
