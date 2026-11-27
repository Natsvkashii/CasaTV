package it.casatv.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import it.casatv.mobile.MainActivity
import it.casatv.mobile.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Keeps live audio/video playback active in the background on Android Mobile
 * and displays an ongoing media notification with a quick stop action.
 */
class MobilePlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            _stopRequests.tryEmit(Unit)
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        val channelName = intent?.getStringExtra(EXTRA_CHANNEL_NAME).orEmpty().ifBlank { "CasaTV" }
        val subtitle = intent?.getStringExtra(EXTRA_CHANNEL_SUBTITLE).orEmpty().ifBlank { "Riproduzione attiva" }
        ensureChannel()
        val notification = buildNotification(channelName, subtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForegroundCompat()
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Riproduzione CasaTV",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Mantiene attiva la riproduzione in background"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, subtitle: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MobilePlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "casatv_mobile_playback"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_STOP = "it.casatv.mobile.action.STOP_PLAYBACK"
        private const val EXTRA_CHANNEL_NAME = "channel_name"
        private const val EXTRA_CHANNEL_SUBTITLE = "channel_subtitle"
        private val _stopRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val stopRequests = _stopRequests.asSharedFlow()

        fun startOrUpdate(context: Context, channelName: String, subtitle: String) {
            val intent = Intent(context, MobilePlaybackService::class.java).apply {
                putExtra(EXTRA_CHANNEL_NAME, channelName)
                putExtra(EXTRA_CHANNEL_SUBTITLE, subtitle)
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, MobilePlaybackService::class.java))
            }
        }
    }
}
