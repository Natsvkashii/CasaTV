package it.casatv.tv.service

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
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import it.casatv.tv.MainActivity
import it.casatv.tv.R
import it.casatv.tv.data.CasaApi
import it.casatv.tv.data.Presence
import it.casatv.tv.data.SystemStatus
import it.casatv.tv.ui.localize
import it.casatv.tv.ui.relativeTime
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps CasaTV alive while the TV app is in the background.
 *
 * Android TV aggressively reclaims background processes. A foreground service
 * with an ongoing (low priority) notification keeps the process and its
 * connections alive, and doubles as a status readout: connection state, last
 * catalog update, connected devices and CPU/RAM/disk usage, with a warning
 * when resources are running out.
 */
class CasaTvService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = CasaApi()

    @Volatile
    private var lastStatus: SystemStatus? = null

    @Volatile
    private var offline = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val deviceId = prefs.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_DEVICE_ID, it).apply()
            }
        api.deviceId = deviceId
        api.deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        api.devicePlatform = "Android TV ${Build.VERSION.RELEASE}"

        startInForeground(buildNotification(null, offline = false))

        scope.launch {
            while (isActive) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf(startId)
    }

    private suspend fun refresh() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val base = prefs.getString(KEY_SERVER_URL, null)
        if (base.isNullOrBlank()) {
            offline = true
            updateNotification(null)
            return
        }
        api.use(base)
        api.useProfile(prefs.getString(KEY_PROFILE_ID, null))
        api.setPlayback(Presence.playbackState, Presence.playbackChannel)
        runCatching { api.heartbeat() }
        try {
            val status = api.system()
            lastStatus = status
            offline = false
            updateNotification(status)
        } catch (_: Exception) {
            offline = true
            updateNotification(lastStatus)
        }
    }

    private fun updateNotification(status: SystemStatus?) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status, offline))
    }

    private fun buildNotification(status: SystemStatus?, offline: Boolean): Notification {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val language = prefs.getString(KEY_LANGUAGE, "it").orEmpty()
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val name = status?.name?.takeIf { it.isNotBlank() } ?: "CasaTV"
        val title = "CasaTV · $name"
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (offline || status == null) {
            val text = localize(
                language,
                "Server non raggiungibile · riprovo in background",
                "Server unreachable · retrying in background",
                "Servidor no disponible · reintentando en segundo plano",
            )
            builder.setContentText(text)
                .setSubText(localize(language, "Offline", "Offline", "Sin conexión"))
            return builder.build()
        }

        val cpu = status.resources.cpu.percent.toInt()
        val ram = status.resources.memory.percent.toInt()
        val disk = status.resources.disk.percent.toInt()
        val lastUpdate = relativeTime(status.lastUpdate.catalog, language)
        val alert = status.alerts.firstOrNull()

        val compact = localize(
            language,
            "v${status.version} · ${status.deviceCount} dispositivi · CPU $cpu% · RAM $ram%",
            "v${status.version} · ${status.deviceCount} devices · CPU $cpu% · RAM $ram%",
            "v${status.version} · ${status.deviceCount} dispositivos · CPU $cpu% · RAM $ram%",
        )
        val details = buildString {
            append(
                localize(
                    language,
                    "Ultimo aggiornamento canali: $lastUpdate",
                    "Last channel update: $lastUpdate",
                    "Última actualización de canales: $lastUpdate",
                ),
            )
            append('\n')
            append(
                localize(
                    language,
                    "Dispositivi connessi: ${status.deviceCount}",
                    "Connected devices: ${status.deviceCount}",
                    "Dispositivos conectados: ${status.deviceCount}",
                ),
            )
            append('\n')
            append("CPU $cpu% · RAM $ram% · ")
            append(localize(language, "Disco", "Disk", "Disco"))
            append(" $disk%")
            val playing = Presence.playbackChannel
            if (Presence.playbackState == "playing" && playing.isNotBlank()) {
                append('\n')
                append(localize(language, "In riproduzione: ", "Playing: ", "Reproduciendo: "))
                append(playing)
            }
            if (alert != null) {
                append('\n')
                append("⚠ ")
                append(alert.message)
                append(
                    " · " + localize(
                        language,
                        "risorse quasi esaurite",
                        "resources almost exhausted",
                        "recursos casi agotados",
                    ),
                )
            }
        }

        builder.setContentText(compact)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
        if (alert != null) {
            builder.setSubText(
                localize(
                    language,
                    "⚠ Risorse quasi esaurite",
                    "⚠ Resources almost exhausted",
                    "⚠ Recursos casi agotados",
                ),
            )
        }
        return builder.build()
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        // Re-creating with the same id keeps the user's importance choice but
        // refreshes the localized name when the profile language changes.
        val language = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "it")
            .orEmpty()
        val channel = NotificationChannel(
            CHANNEL_ID,
            localize(language, "Stato CasaTV", "CasaTV status", "Estado de CasaTV"),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = localize(
                language,
                "Connessione, aggiornamenti e risorse del server",
                "Connection, updates and server resources",
                "Conexión, actualizaciones y recursos del servidor",
            )
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "casatv_status"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 30_000L
        private const val ACTION_STOP = "it.casatv.tv.action.STOP_SERVICE"
        const val PREFS_NAME = "casatv"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_PROFILE_ID = "active_profile_id"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_LANGUAGE = "active_language"

        fun start(context: Context) {
            val intent = Intent(context, CasaTvService::class.java)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CasaTvService::class.java))
        }
    }
}
