package it.casatv.server.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import java.net.InetAddress
import android.util.Log
import androidx.core.app.ServiceCompat
import it.casatv.server.MainActivity
import it.casatv.server.R
import it.casatv.server.core.NetworkUtils
import it.casatv.server.core.PlaylistManager
import it.casatv.server.core.Prefs
import it.casatv.server.core.ServerRuntime
import it.casatv.server.core.textOf
import it.casatv.server.server.CasaHttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the HTTP server alive while the phone screen is off or in deep sleep.
 * Features:
 * - Resilient Foreground Service (CONNECTED_DEVICE) with START_STICKY.
 * - Multi-lock protection: Partial WakeLock, High-Perf Wi-Fi Lock, Multicast Lock (for mDNS).
 * - Autonomous self-healing Watchdog: monitors server socket liveness every 10s and auto-restarts.
 * - Dynamic IP detection: detects DHCP/Wi-Fi reconnects and re-announces via mDNS.
 * - AlarmManager recovery: revives the service if killed by OS low-memory or task removal.
 */
class ServerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null
    private var watchdogJob: Job? = null
    private var server: CasaHttpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var jmdns: javax.jmdns.JmDNS? = null
    private var isExplicitlyStopped = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundEarly()
        startWatchdog()
    }

    private fun startForegroundEarly() {
        val strings = textOf(Prefs.activeLanguage(this))
        val notification = buildNotification(strings)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
            }
            Log.i("CasaTV", "ServerService: startForeground avviato con successo")
        } catch (e: Throwable) {
            Log.w("CasaTV", "ServerService: startForeground con tipo fallito (${e.message}), fallback a standard", e)
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
            } catch (fallbackError: Throwable) {
                Log.e("CasaTV", "ServerService: fallback startForeground fallito", fallbackError)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                isExplicitlyStopped = true
                cancelAlarmWatchdog()
                stopServer()
                stopSelf()
                START_NOT_STICKY
            }
            ACTION_RESTART -> {
                isExplicitlyStopped = false
                restartServerInternal()
                START_STICKY
            }
            else -> {
                isExplicitlyStopped = false
                startServer()
                START_STICKY
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!isExplicitlyStopped && Prefs.autoStart(this)) {
            Log.i("CasaTV", "ServerService: task rimossa, pianifico riavvio autonomo via AlarmManager")
            scheduleRestartAlarm(1000L)
        }
    }

    private fun startServer(): Boolean {
        if (server?.isAlive == true) {
            updateNotification()
            return true
        }
        val port = Prefs.port(this)
        createChannel()
        startForegroundEarly()
        acquireLocks()
        val http = CasaHttpServer(
            context = this,
            nameProvider = { Prefs.serverName(this) },
            languageProvider = { Prefs.activeLanguage(this) },
            port = port,
        )
        return try {
            http.start(SOCKET_READ_TIMEOUT, true)
            server = http
            val ip = NetworkUtils.primaryIp()
            ServerRuntime.setRunning(port, ip)
            registerMdns(port)
            startPlaylistAutoRefresh()
            updateNotification()
            scheduleAlarmWatchdog()
            Log.i("CasaTV", "ServerService avviato autonomamente su http://${ip ?: "0.0.0.0"}:$port")
            true
        } catch (error: Exception) {
            Log.e("CasaTV", "Errore avvio NanoHTTPD: ${error.message}", error)
            server = null
            ServerRuntime.setStopped(error.message ?: "Impossibile avviare il server")
            // Do NOT stopSelf()! Instead, schedule an autonomous retry in 3 seconds.
            serviceScope.launch {
                delay(3000L)
                if (!isExplicitlyStopped && (server == null || server?.isAlive != true)) {
                    Log.i("CasaTV", "Tentativo autonomo di riavvio server...")
                    startServer()
                }
            }
            false
        }
    }

    private fun restartServerInternal() {
        runCatching { server?.stop() }
        server = null
        startServer()
    }

    private var consecutiveHealthFailures = 0

    private fun isServerResponding(port: Int): Boolean {
        return try {
            val url = java.net.URL("http://127.0.0.1:$port/healthz")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = false
            val code = conn.responseCode
            conn.disconnect()
            code == 200
        } catch (_: Exception) {
            false
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                if (!isExplicitlyStopped) {
                    val http = server
                    val port = Prefs.port(this@ServerService)
                    val isAlive = http != null && http.isAlive
                    val isResponding = isAlive && isServerResponding(port)

                    if (!isResponding) {
                        consecutiveHealthFailures++
                        Log.w("CasaTV", "Watchdog: server non risponde ($consecutiveHealthFailures/2), alive=$isAlive")
                        if (consecutiveHealthFailures >= 2) {
                            consecutiveHealthFailures = 0
                            Log.e("CasaTV", "Watchdog: riavvio autonomo del server in corso...")
                            restartServerInternal()
                        }
                    } else {
                        consecutiveHealthFailures = 0
                        // Re-verify lock retention
                        acquireLocks()
                        // Check if network IP changed
                        val currentIp = NetworkUtils.primaryIp()
                        val status = ServerRuntime.status.value
                        if (status.running && currentIp != null && currentIp != status.ip) {
                            Log.i("CasaTV", "Watchdog: IP cambiato (${status.ip} -> $currentIp), aggiorno mDNS")
                            ServerRuntime.setRunning(status.port, currentIp)
                            registerMdns(status.port)
                            updateNotification()
                        }
                    }
                    scheduleAlarmWatchdog()
                }
            }
        }
    }

    private fun startPlaylistAutoRefresh() {
        refreshJob?.cancel()
        val playlistManager = PlaylistManager(this)
        refreshJob = serviceScope.launch {
            while (isActive) {
                delay(AUTO_REFRESH_CHECK_MS)
                val url = Prefs.playlistUrl(this@ServerService)
                val lastImport = Prefs.playlistLastImport(this@ServerService)
                val nowSeconds = System.currentTimeMillis() / 1000
                if (url.isNotBlank() && nowSeconds - lastImport >= PLAYLIST_REFRESH_INTERVAL_SECONDS) {
                    runCatching { playlistManager.refresh() }
                }
            }
        }
    }

    private fun stopServer() {
        watchdogJob?.cancel()
        watchdogJob = null
        refreshJob?.cancel()
        refreshJob = null
        unregisterMdns()
        runCatching { server?.stop() }
        server = null
        releaseLocks()
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        ServerRuntime.setStopped()
    }

    override fun onDestroy() {
        if (!isExplicitlyStopped && Prefs.autoStart(this)) {
            Log.i("CasaTV", "ServerService onDestroy: rilancio autonomo tramite AlarmManager")
            scheduleRestartAlarm(1000L)
        }
        stopServer()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun acquireLocks() {
        if (wakeLock?.isHeld != true) {
            val power = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CasaTV::Server")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wifiLock?.isHeld != true) {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifi?.createWifiLock(mode, "CasaTV::Wifi")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (multicastLock?.isHeld != true) {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("CasaTV::Multicast")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wifiLock = null
        runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        multicastLock = null
    }

    private fun scheduleRestartAlarm(delayMs: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(this, RestartReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            RESTART_ALARM_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val triggerAt = System.currentTimeMillis() + delayMs
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: Exception) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun scheduleAlarmWatchdog() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(this, RestartReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            WATCHDOG_ALARM_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val triggerAt = System.currentTimeMillis() + ALARM_WATCHDOG_TIMEOUT_MS
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: Exception) {
            // Ignored if scheduling not allowed
        }
    }

    private fun cancelAlarmWatchdog() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(this, RestartReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            WATCHDOG_ALARM_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    private fun registerMdns(port: Int) {
        unregisterMdns()
        val ip = NetworkUtils.primaryIp()

        // 1. Android NsdManager (standard DNS-SD for CasaTV TV client)
        val manager = getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (manager != null) {
            nsdManager = manager
            val info = NsdServiceInfo().apply {
                serviceName = "CasaTV Server"
                serviceType = "_casatv._tcp."
                setPort(port)
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    Log.i("CasaTV", "mDNS NSD registrato: ${info.serviceName} porta $port")
                }

                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w("CasaTV", "mDNS NSD non registrato (errore $errorCode)")
                }

                override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            }
            registrationListener = listener
            runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
        }

        // 2. JmDNS Host Responder per "casatv.local" e "_http._tcp.local."
        if (ip != null) {
            serviceScope.launch(Dispatchers.IO) {
                runCatching {
                    val inetAddr = InetAddress.getByName(ip)
                    val j = javax.jmdns.JmDNS.create(inetAddr, "casatv")
                    jmdns = j
                    val httpService = javax.jmdns.ServiceInfo.create(
                        "_http._tcp.local.",
                        "casatv",
                        port,
                        "CasaTV Web Server"
                    )
                    j.registerService(httpService)
                    Log.i("CasaTV", "mDNS JmDNS registrato con successo: http://casatv.local:$port/ su $ip")
                }.onFailure { e ->
                    Log.w("CasaTV", "mDNS JmDNS errore registrazione: ${e.message}")
                }
            }
        }
    }

    private fun unregisterMdns() {
        val manager = nsdManager
        val listener = registrationListener
        if (manager != null && listener != null) {
            runCatching { manager.unregisterService(listener) }
        }
        nsdManager = null
        registrationListener = null

        val j = jmdns
        jmdns = null
        if (j != null) {
            serviceScope.launch(Dispatchers.IO) {
                runCatching {
                    j.unregisterAllServices()
                    j.close()
                }
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val strings = textOf(Prefs.activeLanguage(this))
        val channel = NotificationChannel(
            CHANNEL_ID,
            strings.t("Server CasaTV", "CasaTV server", "Servidor CasaTV"),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = strings.t(
                "Mantiene attivo il server per le TV",
                "Keeps the TV server running",
                "Mantiene activo el servidor para las TV",
            )
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(textOf(Prefs.activeLanguage(this))))
    }

    private fun buildNotification(strings: it.casatv.server.core.Strings): Notification {
        val status = ServerRuntime.status.value
        val address = status.ip?.let { "${it}:${status.port}" } ?: "porta ${status.port}"
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(strings.t("CasaTV Server attivo", "CasaTV server running", "Servidor CasaTV activo"))
            .setContentText(address)
            .setSmallIcon(R.drawable.ic_server)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    null,
                    strings.t("Ferma", "Stop", "Detener"),
                    stop,
                ).build(),
            )
            .build()
    }

    companion object {
        const val ACTION_STOP = "it.casatv.server.STOP"
        const val ACTION_START = "it.casatv.server.START"
        const val ACTION_RESTART = "it.casatv.server.RESTART"
        private const val CHANNEL_ID = "casatv-server"
        private const val NOTIFICATION_ID = 4242
        private const val RESTART_ALARM_ID = 999
        private const val WATCHDOG_ALARM_ID = 998
        private const val SOCKET_READ_TIMEOUT = 15_000
        private const val AUTO_REFRESH_CHECK_MS = 30 * 60 * 1000L
        private const val WATCHDOG_INTERVAL_MS = 10_000L
        private const val ALARM_WATCHDOG_TIMEOUT_MS = 8 * 60 * 1000L
        private const val PLAYLIST_REFRESH_INTERVAL_SECONDS = 6 * 3600L

        fun start(context: Context) {
            val intent = Intent(context, ServerService::class.java).setAction(ACTION_START)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun restart(context: Context) {
            val intent = Intent(context, ServerService::class.java).setAction(ACTION_RESTART)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, ServerService::class.java))
            }
        }
    }
}
