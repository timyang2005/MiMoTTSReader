package com.mimo.ttsreader.model

data class VoiceConfig(
    val voice: String = "冰糖",
    val speed: Int = 5,
    val volume: Int = 5,
    val mimoApiKey: String = "",
    val userMessage: String = "",
    val dialect: String = "",
    val model: String = "mimo-v2.5-tts",
    val audioFormat: String = "wav",
    val serverPort: Int = 9966,
    val autoStart: Boolean = false,
    val styleTag: String = ""
)
