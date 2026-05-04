package com.mimo.ttsreader.server

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mimo.ttsreader.api.MiMoTtsClient
import com.mimo.ttsreader.model.VoiceConfig
import com.mimo.ttsreader.model.VoiceRegistry
import com.mimo.ttsreader.util.AudioUtils
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLDecoder

class TtsServer(
    private val context: Context,
    private val configProvider: () -> VoiceConfig,
    port: Int
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "TtsServer"
    }

    private val ttsClient = MiMoTtsClient()
    private val gson = Gson()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val method = session.method

        Log.d(TAG, "Request: $method $uri")

        val response = try {
            when {
                uri == "/" && method == Method.GET -> serveIndex()
                uri == "/tts" -> handleTtsRequest(session)
                uri == "/api/reader/tts/stream" -> handleReaderTtsStream(session)
                uri == "/api/status" && method == Method.GET -> serveStatus()
                uri == "/api/config" && method == Method.GET -> serveConfig()
                uri == "/api/voices" && method == Method.GET -> serveVoices()
                uri == "/api/legado/rule" && method == Method.GET -> serveLegadoRule()
                uri == "/api/test" -> handleTestTts(session)
                uri.startsWith("/web/") -> serveStaticFile(uri)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling request: $uri", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Internal Server Error: ${e.message}"
            )
        }

        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
        return response
    }

    private fun handleReaderTtsStream(session: IHTTPSession): Response {
        val config = configProvider()

        if (config.mimoApiKey.isBlank()) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                MIME_PLAINTEXT,
                "API Key not configured"
            )
        }

        val params = parseQueryParams(session)
        val text = params["text"] ?: params["speakText"] ?: ""
        val speedStr = params["speed"] ?: params["speakSpeed"] ?: "5"
        val speed = speedStr.toDoubleOrNull()?.toInt() ?: 5

        if (text.isBlank()) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                MIME_PLAINTEXT,
                "Text parameter is required"
            )
        }

        Log.i(TAG, "Reader TTS: text='${text.take(50)}...', speed=$speed, voice=${config.voice}")

        return try {
            val audioData = runBlocking {
                ttsClient.synthesize(
                    apiKey = config.mimoApiKey,
                    text = text,
                    voice = config.voice,
                    model = config.model,
                    userMessage = config.userMessage,
                    dialect = config.dialect,
                    styleTag = config.styleTag,
                    speed = speed
                )
            }

            val wavData = AudioUtils.validateAndFixWav(audioData)

            Log.i(TAG, "Reader TTS success: ${wavData.size} bytes")

            newFixedLengthResponse(
                Response.Status.OK,
                "audio/wav",
                ByteArrayInputStream(wavData),
                wavData.size.toLong()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Reader TTS failed", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "TTS Error: ${e.message}"
            )
        }
    }

    private fun handleTtsRequest(session: IHTTPSession): Response {
        val config = configProvider()

        if (config.mimoApiKey.isBlank()) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                MIME_PLAINTEXT,
                "API Key not configured"
            )
        }

        val params = parseAllParams(session)
        val text = params["tex"] ?: params["text"] ?: params["speakText"] ?: ""
        val speedStr = params["spd"] ?: params["speed"] ?: params["speakSpeed"] ?: "5"
        val speed = speedStr.toDoubleOrNull()?.toInt() ?: 5

        if (text.isBlank()) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                MIME_PLAINTEXT,
                "Text parameter is required (tex/text/speakText)"
            )
        }

        Log.i(TAG, "TTS request: text='${text.take(50)}...', speed=$speed, voice=${config.voice}")

        return try {
            val audioData = runBlocking {
                ttsClient.synthesize(
                    apiKey = config.mimoApiKey,
                    text = text,
                    voice = config.voice,
                    model = config.model,
                    userMessage = config.userMessage,
                    dialect = config.dialect,
                    styleTag = config.styleTag,
                    speed = speed
                )
            }

            val wavData = AudioUtils.validateAndFixWav(audioData)

            Log.i(TAG, "TTS success: ${wavData.size} bytes")

            newFixedLengthResponse(
                Response.Status.OK,
                "audio/wav",
                ByteArrayInputStream(wavData),
                wavData.size.toLong()
            )
        } catch (e: Exception) {
            Log.e(TAG, "TTS synthesis failed", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "TTS Error: ${e.message}"
            )
        }
    }

    private fun handleTestTts(session: IHTTPSession): Response {
        val config = configProvider()
        if (config.mimoApiKey.isBlank()) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "application/json", """{"error":"API Key not configured"}""")
        }

        val testText = when (session.method) {
            Method.GET -> {
                val params = parseQueryParams(session)
                params["text"] ?: "你好，这是一个测试语音。"
            }
            Method.POST -> {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val body = files["postData"] ?: ""
                try {
                    val obj = JsonParser.parseString(body).asJsonObject
                    obj.get("text")?.asString ?: "你好，这是一个测试语音。"
                } catch (_: Exception) {
                    "你好，这是一个测试语音。"
                }
            }
            else -> "你好，这是一个测试语音。"
        }

        return try {
            val audioData = runBlocking {
                ttsClient.synthesize(
                    apiKey = config.mimoApiKey,
                    text = testText,
                    voice = config.voice,
                    model = config.model,
                    userMessage = config.userMessage,
                    dialect = config.dialect,
                    styleTag = config.styleTag,
                    speed = 5
                )
            }
            val wavData = AudioUtils.validateAndFixWav(audioData)
            newFixedLengthResponse(
                Response.Status.OK,
                "audio/wav",
                ByteArrayInputStream(wavData),
                wavData.size.toLong()
            )
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", """{"error":"${e.message?.replace("\"", "\\\"")}"}""")
        }
    }

    private fun serveStatus(): Response {
        val config = configProvider()
        val statusJson = """{"running":true,"port":${config.serverPort},"voice":"${config.voice}","model":"${config.model}"}"""
        return newFixedLengthResponse(Response.Status.OK, "application/json", statusJson)
    }

    private fun serveConfig(): Response {
        val config = configProvider()
        val json = gson.toJson(config)
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun serveVoices(): Response {
        val voices = VoiceRegistry.PRESET_VOICES
        val json = gson.toJson(voices)
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun serveLegadoRule(): Response {
        val config = configProvider()
        val port = config.serverPort
        val rule = hashMapOf<String, Any>(
            "concurrentRate" to "5",
            "contentType" to "audio/wav",
            "enabledCookieJar" to false,
            "header" to "",
            "id" to System.currentTimeMillis(),
            "jsLib" to "",
            "lastUpdateTime" to System.currentTimeMillis(),
            "loginCheckJs" to "",
            "loginUi" to "",
            "loginUrl" to "",
            "name" to "MiMo TTS",
            "url" to "http://localhost:$port/api/reader/tts/stream?text={{java.encodeURI(speakText)}}&speed={{speakSpeed}}"
        )
        val json = gson.toJson(rule)
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun serveIndex(): Response {
        return newFixedLengthResponse(Response.Status.OK, "text/html", """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8"><title>MiMo TTS Reader</title></head>
            <body><h1>MiMo TTS Reader Service</h1><p>Service is running.</p>
            <p><a href="/web/index.html">Open Settings UI</a></p></body></html>
        """.trimIndent())
    }

    private fun serveStaticFile(uri: String): Response {
        val assetPath = uri.removePrefix("/web/")
        if (assetPath.isBlank() || assetPath == "/") {
            return serveStaticAsset("web/index.html", "text/html")
        }

        val mimeType = when {
            assetPath.endsWith(".html") -> "text/html"
            assetPath.endsWith(".css") -> "text/css"
            assetPath.endsWith(".js") -> "application/javascript"
            assetPath.endsWith(".json") -> "application/json"
            assetPath.endsWith(".png") -> "image/png"
            assetPath.endsWith(".svg") -> "image/svg+xml"
            else -> "application/octet-stream"
        }

        return serveStaticAsset("web/$assetPath", mimeType)
    }

    private fun serveStaticAsset(assetPath: String, mimeType: String): Response {
        return try {
            val inputStream: InputStream = context.assets.open(assetPath)
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found: $assetPath")

            val bytes = inputStream.readBytes()
            inputStream.close()
            newFixedLengthResponse(Response.Status.OK, mimeType, ByteArrayInputStream(bytes), bytes.size.toLong())
        } catch (e: Exception) {
            Log.w(TAG, "Static file not found: $assetPath")
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found: $assetPath")
        }
    }

    private fun parseQueryParams(session: IHTTPSession): Map<String, String> {
        val result = mutableMapOf<String, String>()
        session.parms?.forEach { (key, value) ->
            if (value != null) {
                result[key] = value
            }
        }
        return result
    }

    private fun parseAllParams(session: IHTTPSession): Map<String, String> {
        val result = mutableMapOf<String, String>()

        session.parms?.forEach { (key, value) ->
            if (value != null) {
                result[key] = value
            }
        }

        if (session.method == Method.POST) {
            try {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val body = files["postData"] ?: ""
                if (body.isNotBlank()) {
                    parseUrlEncodedParams(body, result)
                }
            } catch (_: Exception) {
            }
        }

        return result
    }

    private fun parseUrlEncodedParams(body: String, result: MutableMap<String, String>) {
        body.split("&").forEach { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                try {
                    val key = URLDecoder.decode(parts[0], "UTF-8")
                    val value = URLDecoder.decode(parts[1], "UTF-8")
                    result[key] = value
                } catch (_: Exception) {
                }
            }
        }
    }
}
