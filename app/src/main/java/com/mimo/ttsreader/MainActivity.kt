package com.mimo.ttsreader

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.mimo.ttsreader.model.VoiceConfig
import com.mimo.ttsreader.model.VoiceRegistry
import com.mimo.ttsreader.service.TtsService
import com.mimo.ttsreader.util.ConfigManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var webView: WebView? = null
    private var logUpdateJob: kotlinx.coroutines.Job? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissions()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            addJavascriptInterface(WebAppInterface(), "Android")
        }

        setContentView(webView)

        webView!!.loadUrl("file:///android_asset/web/index.html")

        observeServiceState()
    }

    private fun observeServiceState() {
        lifecycleScope.launch {
            TtsService.isRunning.collect { running ->
                webView?.post {
                    webView?.evaluateJavascript(
                        "if(typeof onServiceStateChanged==='function'){onServiceStateChanged($running);}",
                        null
                    )
                }
            }
        }

        lifecycleScope.launch {
            TtsService.logs.collect { logs ->
                webView?.post {
                    try {
                        val json = Gson().toJson(logs.take(50))
                        webView?.evaluateJavascript(
                            "if(typeof onLogsUpdate==='function'){onLogsUpdate($json);}",
                            null
                        )
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webView?.post {
            val running = TtsService.isRunning.value
            webView?.evaluateJavascript(
                "if(typeof onServiceStateChanged==='function'){onServiceStateChanged($running);}",
                null
            )
            refreshLogs()
        }
    }

    private fun refreshLogs() {
        try {
            val json = Gson().toJson(TtsService.logs.value.take(50))
            webView?.evaluateJavascript(
                "if(typeof onLogsUpdate==='function'){onLogsUpdate($json);}",
                null
            )
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        logUpdateJob?.cancel()
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }

    inner class WebAppInterface {

        @JavascriptInterface
        fun getConfig(): String {
            val config = ConfigManager.getConfig(this@MainActivity)
            return Gson().toJson(config)
        }

        @JavascriptInterface
        fun saveConfig(json: String) {
            try {
                val config = Gson().fromJson(json, VoiceConfig::class.java)
                ConfigManager.saveConfig(this@MainActivity, config)
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun startService() {
            val intent = Intent(this@MainActivity, TtsService::class.java).apply {
                action = TtsService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        @JavascriptInterface
        fun stopService() {
            val intent = Intent(this@MainActivity, TtsService::class.java).apply {
                action = TtsService.ACTION_STOP
            }
            startService(intent)
        }

        @JavascriptInterface
        fun restartService() {
            val intent = Intent(this@MainActivity, TtsService::class.java).apply {
                action = TtsService.ACTION_RESTART
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        @JavascriptInterface
        fun isServiceRunning(): Boolean {
            return TtsService.isRunning.value
        }

        @JavascriptInterface
        fun getVoices(): String {
            return Gson().toJson(VoiceRegistry.PRESET_VOICES)
        }

        @JavascriptInterface
        fun getDialects(): String {
            return Gson().toJson(VoiceRegistry.DIALECT_STYLES.keys.filter { it.isNotEmpty() }
                .plus("")
                .toList())
        }

        @JavascriptInterface
        fun getLegadoRule(): String {
            val config = ConfigManager.getConfig(this@MainActivity)
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
            return Gson().toJson(rule)
        }

        @JavascriptInterface
        fun importToLegado() {
            val ruleJson = getLegadoRule()
            try {
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, ruleJson)
                }
                runOnUiThread {
                    startActivity(Intent.createChooser(intent, "Import to Legado Reader"))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun testTts(text: String) {
            if (!TtsService.isRunning.value) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "请先启动服务", Toast.LENGTH_SHORT).show()
                }
                return
            }

            val config = ConfigManager.getConfig(this@MainActivity)
            if (config.mimoApiKey.isBlank()) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "请先配置 API Key", Toast.LENGTH_SHORT).show()
                }
                return
            }

            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val client = com.mimo.ttsreader.api.MiMoTtsClient()
                    val audioData = client.synthesize(
                        apiKey = config.mimoApiKey,
                        text = text,
                        voice = config.voice,
                        model = config.model,
                        userMessage = config.userMessage,
                        dialect = config.dialect,
                        styleTag = config.styleTag,
                        speed = 5
                    )
                    val wavData = com.mimo.ttsreader.util.AudioUtils.validateAndFixWav(audioData)

                    val file = java.io.File.createTempFile("tts_test_", ".wav", cacheDir)
                    file.writeBytes(wavData)
                    file.deleteOnExit()

                    runOnUiThread {
                        webView?.evaluateJavascript(
                            "if(typeof onTestResult==='function'){onTestResult('file://${file.absolutePath}');}",
                            null
                        )
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        webView?.evaluateJavascript(
                            "if(typeof onTestError==='function'){onTestError('${e.message?.replace("'", "\\'")}');}",
                            null
                        )
                    }
                }
            }
        }

        @JavascriptInterface
        fun requestBatteryOptimization() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${packageName}")
                )
                runOnUiThread { startActivity(intent) }
            }
        }

        @JavascriptInterface
        fun getLogs(): String {
            return Gson().toJson(TtsService.logs.value.take(50))
        }

        @JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun getServerUrl(): String {
            val config = ConfigManager.getConfig(this@MainActivity)
            return "http://localhost:${config.serverPort}"
        }
    }
}
