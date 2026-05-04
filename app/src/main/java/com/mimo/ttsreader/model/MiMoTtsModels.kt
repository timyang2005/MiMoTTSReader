package com.mimo.ttsreader.model

data class MiMoTtsRequest(
    val model: String,
    val messages: List<Message>,
    val audio: AudioConfig,
    val stream: Boolean = false
)

data class Message(
    val role: String,
    val content: String
)

data class AudioConfig(
    val format: String,
    val voice: String
)

data class MiMoTtsResponse(
    val choices: List<Choice>
) {
    data class Choice(
        val message: MessageResponse
    )

    data class MessageResponse(
        val audio: AudioData?
    )

    data class AudioData(
        val data: String,
        val expires_at: Long? = null,
        val transcript: String? = null
    )
}

data class MiMoStreamChunk(
    val choices: List<StreamChoice>
) {
    data class StreamChoice(
        val delta: StreamDelta
    )

    data class StreamDelta(
        val audio: AudioData? = null
    )

    data class AudioData(
        val data: String
    )
}

data class VoiceInfo(
    val id: String,
    val name: String,
    val language: String,
    val gender: String
)

object VoiceRegistry {
    val PRESET_VOICES = listOf(
        VoiceInfo("mimo_default", "MiMo-默认", "中英", "混合"),
        VoiceInfo("冰糖", "冰糖", "中文", "女性"),
        VoiceInfo("茉莉", "茉莉", "中文", "女性"),
        VoiceInfo("苏打", "苏打", "中文", "男性"),
        VoiceInfo("白桦", "白桦", "中文", "男性"),
        VoiceInfo("Mia", "Mia", "英文", "女性"),
        VoiceInfo("Chloe", "Chloe", "英文", "女性"),
        VoiceInfo("Milo", "Milo", "英文", "男性"),
        VoiceInfo("Dean", "Dean", "英文", "男性"),
    )

    val DIALECT_STYLES = mapOf(
        "" to "",
        "东北话" to "(东北话)",
        "四川话" to "(四川话)",
        "河南话" to "(河南话)",
        "粤语" to "(粤语)"
    )

    fun getVoiceId(name: String): String {
        return PRESET_VOICES.find { it.name == name || it.id == name }?.id ?: "冰糖"
    }

    fun getDialectTag(dialect: String): String {
        return DIALECT_STYLES[dialect] ?: ""
    }
}
