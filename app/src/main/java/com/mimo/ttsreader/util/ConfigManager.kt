package com.mimo.ttsreader.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.mimo.ttsreader.model.VoiceConfig

object ConfigManager {

    private const val PREFS_NAME = "mimo_tts_reader_prefs"
    private const val KEY_VOICE_CONFIG = "voice_config"

    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getConfig(context: Context): VoiceConfig {
        val json = getPrefs(context).getString(KEY_VOICE_CONFIG, null)
        return if (json != null) {
            try {
                gson.fromJson(json, VoiceConfig::class.java)
            } catch (_: Exception) {
                VoiceConfig()
            }
        } else {
            VoiceConfig()
        }
    }

    fun saveConfig(context: Context, config: VoiceConfig) {
        val json = gson.toJson(config)
        getPrefs(context).edit().putString(KEY_VOICE_CONFIG, json).apply()
    }

    fun getApiKey(context: Context): String {
        return getConfig(context).mimoApiKey
    }

    fun getServerPort(context: Context): Int {
        return getConfig(context).serverPort
    }
}
