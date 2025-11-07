package com.example.mirroringapp.mirroring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.mirroringapp.R

class MirroringService : Service() {

    private var session: MirroringSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMirroring(intent)
            ACTION_STOP -> stopMirroring()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMirroring()
    }

    private fun startMirroring(intent: Intent) {
        val projectionData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_PROJECTION_DATA)
        } ?: return
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val optionName = intent.getStringExtra(EXTRA_CONNECTION_OPTION) ?: ConnectionOption.USB_C.name
        val connectionOption = runCatching { ConnectionOption.valueOf(optionName) }.getOrDefault(ConnectionOption.USB_C)
        val lowLatency = intent.getBooleanExtra(EXTRA_LOW_LATENCY, true)
        val hardwareEncoder = intent.getBooleanExtra(EXTRA_HARDWARE_ENCODER, true)

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        val mediaProjection = projectionManager.getMediaProjection(resultCode, projectionData)
        session?.stop()
        session = MirroringSession(
            context = this,
            projection = mediaProjection,
            connectionOption = connectionOption,
            lowLatency = lowLatency,
            hardwareEncoder = hardwareEncoder
        ).also { session ->
            session.start()
        }

        startForeground(NOTIFICATION_ID, buildNotification(connectionOption))
    }

    private fun stopMirroring() {
        session?.stop()
        session = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(option: ConnectionOption): Notification {
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.external_display_connected))
            .setSubText(option.name)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Screen mirroring",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Keeps screen mirroring active"
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val ACTION_START = "com.example.mirroringapp.action.START"
        private const val ACTION_STOP = "com.example.mirroringapp.action.STOP"
        private const val EXTRA_PROJECTION_DATA = "extra_projection_data"
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_CONNECTION_OPTION = "extra_connection_option"
        private const val EXTRA_LOW_LATENCY = "extra_low_latency"
        private const val EXTRA_HARDWARE_ENCODER = "extra_hardware_encoder"
        private const val NOTIFICATION_CHANNEL_ID = "mirroring_channel"
        private const val NOTIFICATION_ID = 1001

        fun createStartIntent(
            context: Context,
            resultCode: Int,
            projectionData: Intent,
            connectionOption: ConnectionOption,
            lowLatency: Boolean,
            hardwareEncoder: Boolean
        ): Intent {
            return Intent(context, MirroringService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_PROJECTION_DATA, projectionData)
                putExtra(EXTRA_CONNECTION_OPTION, connectionOption.name)
                putExtra(EXTRA_LOW_LATENCY, lowLatency)
                putExtra(EXTRA_HARDWARE_ENCODER, hardwareEncoder)
            }
        }

        fun createStopIntent(context: Context): Intent {
            return Intent(context, MirroringService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}
