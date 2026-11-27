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
import it.casatv.server.core.LogoCache
import it.casatv.server.core.NetworkUtils
import it.casatv.server.core.PlaylistManager
import it.casatv.server.core.Prefs
import it.casatv.server.core.ServerDatabase
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
    private val serverLock = Any()
    private var refreshJob: Job? = null
    private var watchdogJob: Job? = null
    private var logoPrewarmJob: Job? = null
    private var retryJob: Job? = null
    @Volatile
    private var server: CasaHttpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    @Volatile
    private var jmdns: javax.jmdns.JmDNS? = null
    @Volatile
    private var jmdnsV6: javax.jmdns.JmDNS? = null
    private var mdnsJob: Job? = null
    private val mdnsGeneration = java.util.concurrent.atomic.AtomicLong(0L)
    @Volatile
    private var isExplicitlyStopped = false
    @Volatile
    private var lastAlarmWatchdogAtMs = 0L
    @Volatile
    private var lastDiagPruneAtMs = 0L

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
                stopSelf()
            }
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w("CasaTV", "ServerService: raggiunto limite temporale foreground service ($fgsType), arresto pulito")
        stopServer()
        if (!isExplicitlyStopped && Prefs.autoStart(this)) {
            scheduleRestartAlarm(2000L)
        }
        stopSelf(startId)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundEarly()
        return when (intent?.action) {
            ACTION_STOP -> {
                isExplicitlyStopped = true
                cancelAlarmWatchdog()
                serviceScope.launch {
                    stopServer()
                    stopSelf()
                }
                START_NOT_STICKY
            }
            ACTION_RESTART -> {
                isExplicitlyStopped = false
                serviceScope.launch {
                    restartServerInternal()
                }
                START_STICKY
            }
            else -> {
                isExplicitlyStopped = false
                serviceScope.launch {
                    startServer()
                }
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

    private fun startServer(): Boolean = synchronized(serverLock) {
        if (server?.isAlive == true) {
            retryJob?.cancel()
            retryJob = null
            updateNotification()
            return@synchronized true
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
        return@synchronized try {
            http.start(SOCKET_READ_TIMEOUT, true)
            server = http
            retryJob?.cancel()
            retryJob = null
            val ip = NetworkUtils.primaryIp()
            ServerRuntime.setRunning(port, ip)
            registerMdns(port)
            startPlaylistAutoRefresh()
            startLogoPrewarm()
            serviceScope.launch {
                val pruned = runCatching { ServerDatabase.get(this@ServerService).pruneOldDiagnosticLogs() }.getOrDefault(0)
                lastDiagPruneAtMs = System.currentTimeMillis()
                if (pruned > 0) Log.i("CasaTV", "Diagnostica: eliminati $pruned log piu vecchi di 14 giorni")
            }
            updateNotification()
            scheduleAlarmWatchdog()
            Log.i("CasaTV", "ServerService avviato autonomamente su http://${ip ?: "0.0.0.0"}:$port")
            true
        } catch (error: Exception) {
            Log.e("CasaTV", "Errore avvio NanoHTTPD: ${error.message}", error)
            runCatching { http.stop() }
            if (server?.isAlive == true) {
                Log.w("CasaTV", "Instanza server esistente ancora attiva su porta $port, mantengo attiva")
                return@synchronized true
            }
            server = null
            ServerRuntime.setStopped(error.message ?: "Impossibile avviare il server")
            // Schedule a single cancellable retry in 4 seconds if no server is running.
            retryJob?.cancel()
            retryJob = serviceScope.launch {
                delay(4000L)
                if (!isExplicitlyStopped && server?.isAlive != true) {
                    Log.i("CasaTV", "Tentativo autonomo di riavvio server...")
                    startServer()
                }
            }
            false
        }
    }

    private fun restartServerInternal() = synchronized(serverLock) {
        retryJob?.cancel()
        retryJob = null
        runCatching { server?.stop() }
        server = null
        startServer()
    }

    @Volatile
    private var consecutiveHealthFailures = 0

    private fun isServerResponding(port: Int): Boolean {
        return try {
            val url = java.net.URL("http://127.0.0.1:$port/healthz")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = false
            val code = conn.responseCode
            runCatching { conn.inputStream?.use { it.readBytes() } }
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
                        val maxFailures = if (isAlive) 4 else 2
                        Log.w("CasaTV", "Watchdog: server non risponde ($consecutiveHealthFailures/$maxFailures), alive=$isAlive")
                        if (consecutiveHealthFailures >= maxFailures) {
                            val failures = consecutiveHealthFailures
                            consecutiveHealthFailures = 0
                            Log.e("CasaTV", "Watchdog: riavvio autonomo del server in corso...")
                            it.casatv.server.core.ServerDiagnosticLogger.get(this@ServerService).log(
                                code = it.casatv.server.core.DiagnosticCodes.SRV_WATCHDOG_RESTART,
                                severity = "error",
                                message = "Watchdog: riavvio autonomo del server (alive=$isAlive, failures=$failures)",
                                details = mapOf("alive" to isAlive, "failures" to failures),
                            )
                            restartServerInternal()
                        }
                    } else {
                        consecutiveHealthFailures = 0
                        // Re-verify lock retention safely
                        synchronized(serverLock) {
                            if (!isExplicitlyStopped && server?.isAlive == true) {
                                acquireLocks()
                            }
                        }
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
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastAlarmWatchdogAtMs >= ALARM_RESCHEDULE_MIN_MS) {
                        lastAlarmWatchdogAtMs = nowMs
                        scheduleAlarmWatchdog()
                    }
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
                val lastImport = Prefs.playlistLastImport(this@ServerService)
                val nowSeconds = System.currentTimeMillis() / 1000
                if (nowSeconds - lastImport >= PLAYLIST_REFRESH_INTERVAL_SECONDS) {
                    val refreshed = runCatching { playlistManager.refreshAllEnabled() }.getOrDefault(0)
                    if (refreshed > 0) startLogoPrewarm()
                }
                val nowMs = System.currentTimeMillis()
                if (nowMs - lastDiagPruneAtMs >= DIAGNOSTIC_PRUNE_INTERVAL_MS) {
                    lastDiagPruneAtMs = nowMs
                    val pruned = runCatching { ServerDatabase.get(this@ServerService).pruneOldDiagnosticLogs() }.getOrDefault(0)
                    if (pruned > 0) Log.i("CasaTV", "Diagnostica: eliminati $pruned log piu vecchi di 14 giorni")
                }
            }
        }
    }

    private fun startLogoPrewarm() {
        logoPrewarmJob?.cancel()
        logoPrewarmJob = serviceScope.launch {
            delay(15_000L)
            val db = ServerDatabase.get(this@ServerService)
            val profiles = db.listProfiles()
            val favIds = profiles.flatMap { db.favoriteIds(it.id) }.toSet()
            val preferredCountries = (profiles.map { it.country } + "IT")
                .map { it.trim().uppercase() }
                .filter { it.isNotBlank() }
                .toSet()
            val channels = buildList {
                if (favIds.isNotEmpty()) {
                    addAll(db.getChannelsByIds(favIds.take(250)).values)
                }
                for (cc in preferredCountries) {
                    addAll(db.queryChannels(country = cc, limit = 150).first)
                }
            }
            val urls = ArrayList<String>(512)
            for (ch in channels) {
                if (ch.id in favIds && !ch.logo.isNullOrBlank()) {
                    urls.add(ch.logo)
                }
            }
            for (ch in channels) {
                if (ch.id !in favIds && ch.country.uppercase() in preferredCountries && !ch.logo.isNullOrBlank()) {
                    urls.add(ch.logo)
                }
            }
            val cache = LogoCache.getInstance(this@ServerService)
            val pending = urls.asSequence()
                .distinct()
                .filter { !cache.isCachedOnDisk(it) }
                .take(500)
                .toList()
            if (pending.isNotEmpty()) {
                cache.prefetchUrls(pending, this)
            }
        }
    }

    private fun stopServer() = synchronized(serverLock) {
        retryJob?.cancel()
        retryJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        refreshJob?.cancel()
        refreshJob = null
        logoPrewarmJob?.cancel()
        logoPrewarmJob = null
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
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: Exception) {
            runCatching { alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent) }
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
            val gen = mdnsGeneration.incrementAndGet()
            mdnsJob = serviceScope.launch(Dispatchers.IO) {
                runCatching {
                    val inetAddr = InetAddress.getByName(ip)
                    val j = javax.jmdns.JmDNS.create(inetAddr, "casatv")
                    if (mdnsGeneration.get() != gen || isExplicitlyStopped) {
                        runCatching {
                            j.unregisterAllServices()
                            j.close()
                        }
                        return@launch
                    }
                    jmdns = j
                    val httpService = javax.jmdns.ServiceInfo.create(
                        "_http._tcp.local.",
                        "casatv",
                        port,
                        "CasaTV Web Server"
                    )
                    j.registerService(httpService)
                    Log.i("CasaTV", "mDNS JmDNS IPv4 registrato: http://casatv.local:$port/ su $ip")

                    // Rispondi anche alle query IPv6 (AAAA) sullo stesso host casatv.local per evitare
                    // il timeout DNS di ~5s da macOS/iOS che interrogano A + AAAA in parallelo.
                    val nif = java.net.NetworkInterface.getByInetAddress(inetAddr)
                    val v6Addrs = nif?.inetAddresses?.asSequence()
                        ?.filterIsInstance<java.net.Inet6Address>()
                        ?.filter { !it.isLoopbackAddress && !it.isMulticastAddress }
                        ?.toList()
                        .orEmpty()
                    val v6Addr = v6Addrs.firstOrNull { !it.isLinkLocalAddress }
                    if (v6Addr != null && mdnsGeneration.get() == gen && !isExplicitlyStopped) {
                        runCatching {
                            val j6 = javax.jmdns.JmDNS.create(v6Addr, "casatv")
                            if (mdnsGeneration.get() != gen || isExplicitlyStopped) {
                                runCatching {
                                    j6.unregisterAllServices()
                                    j6.close()
                                }
                            } else {
                                jmdnsV6 = j6
                                val httpServiceV6 = javax.jmdns.ServiceInfo.create(
                                    "_http._tcp.local.",
                                    "casatv-v6",
                                    port,
                                    "CasaTV Web Server IPv6"
                                )
                                j6.registerService(httpServiceV6)
                                Log.i("CasaTV", "mDNS JmDNS IPv6 registrato: casatv.local su ${v6Addr.hostAddress}")
                            }
                        }.onFailure { e6 ->
                            Log.w("CasaTV", "mDNS JmDNS IPv6 ignorato: ${e6.message}")
                        }
                    }
                }.onFailure { e ->
                    Log.w("CasaTV", "mDNS JmDNS errore registrazione: ${e.message}")
                }
            }
        }
    }

    private fun unregisterMdns() {
        mdnsGeneration.incrementAndGet()
        mdnsJob?.cancel()
        mdnsJob = null
        val manager = nsdManager
        val listener = registrationListener
        if (manager != null && listener != null) {
            runCatching { manager.unregisterService(listener) }
        }
        nsdManager = null
        registrationListener = null

        val j = jmdns
        jmdns = null
        val j6 = jmdnsV6
        jmdnsV6 = null
        if (j != null || j6 != null) {
            Thread(
                {
                    if (j != null) {
                        runCatching {
                            j.unregisterAllServices()
                            j.close()
                        }
                    }
                    if (j6 != null) {
                        runCatching {
                            j6.unregisterAllServices()
                            j6.close()
                        }
                    }
                },
                "CasaTV-JmDNS-Close",
            ).apply { isDaemon = true }.start()
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
        private const val SOCKET_READ_TIMEOUT = 30_000
        private const val AUTO_REFRESH_CHECK_MS = 30 * 60 * 1000L
        private const val WATCHDOG_INTERVAL_MS = 10_000L
        private const val ALARM_RESCHEDULE_MIN_MS = 5 * 60 * 1000L
        private const val ALARM_WATCHDOG_TIMEOUT_MS = 8 * 60 * 1000L
        private const val PLAYLIST_REFRESH_INTERVAL_SECONDS = 6 * 3600L
        private const val DIAGNOSTIC_PRUNE_INTERVAL_MS = 6L * 60L * 60L * 1000L

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
            val intent = Intent(context, ServerService::class.java).setAction(ACTION_STOP)
            val sent = runCatching { context.startService(intent) }.isSuccess
            if (!sent) {
                runCatching { context.stopService(Intent(context, ServerService::class.java)) }
            }
        }
    }
}
