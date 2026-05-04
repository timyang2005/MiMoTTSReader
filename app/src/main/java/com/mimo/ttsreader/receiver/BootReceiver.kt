package com.mimo.ttsreader.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mimo.ttsreader.service.TtsService
import com.mimo.ttsreader.util.ConfigManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.REBOOT" -> {
                val config = ConfigManager.getConfig(context)
                if (config.autoStart) {
                    val serviceIntent = Intent(context, TtsService::class.java).apply {
                        action = TtsService.ACTION_START
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }
}
