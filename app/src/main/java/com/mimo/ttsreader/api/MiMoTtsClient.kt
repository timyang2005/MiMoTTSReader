package com.mimo.ttsreader.api

import android.util.Base64
import com.google.gson.Gson
import com.mimo.ttsreader.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class MiMoTtsClient {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun synthesize(
        apiKey: String,
        text: String,
        voice: String = "冰糖",
        model: String = "mimo-v2.5-tts",
        userMessage: String = "",
        dialect: String = "",
        styleTag: String = "",
        speed: Int = 5
    ): ByteArray = withContext(Dispatchers.IO) {
        val voiceId = VoiceRegistry.getVoiceId(voice)
        val dialectTag = VoiceRegistry.getDialectTag(dialect)
        val speedInstruction = buildSpeedInstruction(speed)

        val stylePrefix = if (styleTag.isNotEmpty()) "($styleTag)" else ""
        val dialectPrefix = if (dialectTag.isNotEmpty()) dialectTag else ""
        val assistantContent = "$dialectPrefix$stylePrefix$text"

        val userContent = buildUserContent(userMessage, speedInstruction, dialect)

        val request = MiMoTtsRequest(
            model = model,
            messages = listOf(
                Message(role = "user", content = userContent),
                Message(role = "assistant", content = assistantContent)
            ),
            audio = AudioConfig(format = "wav", voice = voiceId),
            stream = false
        )

        val json = gson.toJson(request)
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

        val httpRequest = Request.Builder()
            .url("https://api.xiaomimimo.com/v1/chat/completions")
            .addHeader("api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw RuntimeException("MiMo API error ${response.code}: $errorBody")
            }

            val responseBody = response.body?.string()
                ?: throw RuntimeException("Empty response body")

            val ttsResponse = gson.fromJson(responseBody, MiMoTtsResponse::class.java)
            val audioData = ttsResponse.choices.firstOrNull()
                ?.message?.audio?.data
                ?: throw RuntimeException("No audio data in response")

            Base64.decode(audioData, Base64.DEFAULT)
        }
    }

    suspend fun synthesizeStream(
        apiKey: String,
        text: String,
        voice: String = "冰糖",
        model: String = "mimo-v2.5-tts",
        userMessage: String = "",
        dialect: String = "",
        styleTag: String = "",
        speed: Int = 5
    ): ByteArray = withContext(Dispatchers.IO) {
        val voiceId = VoiceRegistry.getVoiceId(voice)
        val dialectTag = VoiceRegistry.getDialectTag(dialect)
        val speedInstruction = buildSpeedInstruction(speed)

        val stylePrefix = if (styleTag.isNotEmpty()) "($styleTag)" else ""
        val dialectPrefix = if (dialectTag.isNotEmpty()) dialectTag else ""
        val assistantContent = "$dialectPrefix$stylePrefix$text"

        val userContent = buildUserContent(userMessage, speedInstruction, dialect)

        val request = MiMoTtsRequest(
            model = model,
            messages = listOf(
                Message(role = "user", content = userContent),
                Message(role = "assistant", content = assistantContent)
            ),
            audio = AudioConfig(format = "pcm16", voice = voiceId),
            stream = true
        )

        val json = gson.toJson(request)
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

        val httpRequest = Request.Builder()
            .url("https://api.xiaomimimo.com/v1/chat/completions")
            .addHeader("api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val pcmBuffer = ByteArrayOutputStream()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                throw RuntimeException("MiMo API error ${response.code}: $errorBody")
            }

            val source = response.body?.source()
                ?: throw RuntimeException("Empty response body")

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue

                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break

                    try {
                        val chunk = gson.fromJson(data, MiMoStreamChunk::class.java)
                        val audioData = chunk.choices.firstOrNull()?.delta?.audio?.data
                        if (audioData != null) {
                            val pcmBytes = Base64.decode(audioData, Base64.DEFAULT)
                            pcmBuffer.write(pcmBytes)
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        val pcmData = pcmBuffer.toByteArray()
        com.mimo.ttsreader.util.AudioUtils.pcm16ToWav(pcmData, 24000, 1, 16)
    }

    private fun buildSpeedInstruction(speed: Int): String {
        return when {
            speed <= 2 -> "语速很慢，每个字都慢慢说"
            speed <= 4 -> "语速偏慢，从容不迫"
            speed <= 6 -> "正常语速，自然流畅"
            speed <= 8 -> "语速偏快，节奏明快"
            else -> "语速很快，快速朗读"
        }
    }

    private fun buildUserContent(userMessage: String, speedInstruction: String, dialect: String): String {
        val parts = mutableListOf<String>()
        if (userMessage.isNotBlank()) {
            parts.add(userMessage)
        }
        parts.add(speedInstruction)
        return parts.joinToString("。")
    }
}
