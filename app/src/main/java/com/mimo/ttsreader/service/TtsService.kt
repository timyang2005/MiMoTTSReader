package com.mimo.ttsreader.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mimo.ttsreader.MainActivity
import com.mimo.ttsreader.model.VoiceConfig
import com.mimo.ttsreader.server.TtsServer
import com.mimo.ttsreader.util.ConfigManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TtsService : Service() {

    companion object {
        private const val TAG = "TtsService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "mimo_tts_channel"

        const val ACTION_START = "com.mimo.ttsreader.ACTION_START"
        const val ACTION_STOP = "com.mimo.ttsreader.ACTION_STOP"
        const val ACTION_RESTART = "com.mimo.ttsreader.ACTION_RESTART"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _logs = MutableStateFlow<List<String>>(emptyList())
        val logs: StateFlow<List<String>> = _logs

        fun addLog(message: String) {
            val current = _logs.value.toMutableList()
            current.add(0, "[${System.currentTimeMillis().formatTime()}] $message")
            if (current.size > 200) current.removeLast()
            _logs.value = current
        }

        private fun Long.formatTime(): String {
            val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(this))
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var server: TtsServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentConfig: VoiceConfig? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        addLog("Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                stopServer()
                startServer()
            }
            else -> {
                startServer()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopServer()
        serviceScope.cancel()
        super.onDestroy()
        addLog("Service destroyed")
    }

    private fun startServer() {
        if (server != null && _isRunning.value) {
            addLog("Server already running")
            return
        }

        currentConfig = ConfigManager.getConfig(this)

        if (currentConfig?.mimoApiKey.isNullOrBlank()) {
            addLog("Warning: API Key not configured")
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                val port = currentConfig?.serverPort ?: 9966
                server = TtsServer(
                    context = this@TtsService,
                    configProvider = { ConfigManager.getConfig(this@TtsService) },
                    port = port
                )
                server!!.start()

                _isRunning.value = true
                addLog("Server started on port $port")

                acquireWakeLock()
                showNotification(port)

                withContext(Dispatchers.Main) {
                    addLog("MiMo TTS Reader ready - http://localhost:$port")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
                addLog("Error starting server: ${e.message}")
                _isRunning.value = false
            }
        }
    }

    private fun stopServer() {
        try {
            server?.stop()
            server = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }

        releaseWakeLock()
        _isRunning.value = false
        addLog("Server stopped")
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MiMoTTSReader::TtsWakeLock"
        )
        wakeLock?.acquire(12 * 60 * 60 * 1000L)
        addLog("Wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                addLog("Wake lock released")
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MiMo TTS Reader",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "TTS service status"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(port: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TtsService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MiMo TTS Reader")
            .setContentText("Running on port $port")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }
}
