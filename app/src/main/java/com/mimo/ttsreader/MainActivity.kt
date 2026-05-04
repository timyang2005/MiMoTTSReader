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
                    val json = Gson().toJson(logs.take(50))
                    webView?.evaluateJavascript(
                        "if(typeof onLogsUpdate==='function'){onLogsUpdate($json);}",
                        null
                    )
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
        }
    }

    override fun onDestroy() {
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
                .plus("") // include empty option
                .toList())
        }

        @JavascriptInterface
        fun getLegadoRule(): String {
            val config = ConfigManager.getConfig(this@MainActivity)
            val port = config.serverPort
            return "http://localhost:$port/tts,{\"method\":\"POST\",\"body\":\"tex={{java.encodeURI(java.encodeURI(speakText))}}&spd={{String((speakSpeed+5)/10+4)}}&_res_tag_=audio\"}"
        }

        @JavascriptInterface
        fun importToLegado() {
            val rule = getLegadoRule()
            try {
                val intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, rule)
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
