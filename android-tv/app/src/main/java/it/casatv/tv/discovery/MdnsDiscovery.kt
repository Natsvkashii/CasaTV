package it.casatv.tv.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL

data class DiscoveredServer(
    val serviceName: String,
    val name: String,
    val url: String,
)

/**
 * Dual discovery engine:
 * 1. Android NSD (mDNS) discovering "_casatv._tcp."
 * 2. Parallel LAN Subnet Probe on port 8787 for instant resilience on routers
 *    where multicast/mDNS is filtered or isolated.
 */
class MdnsDiscovery(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val resolving = mutableSetOf<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var subnetJob: Job? = null

    private val _servers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val servers: StateFlow<List<DiscoveredServer>> = _servers.asStateFlow()

    private val _discovering = MutableStateFlow(false)
    val discovering: StateFlow<Boolean> = _discovering.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun addServer(server: DiscoveredServer) {
        val current = _servers.value
        if (current.none { it.url == server.url }) {
            _servers.value = (current + server).sortedBy { it.name.lowercase() }
        }
    }

    @Suppress("DEPRECATION")
    fun start() {
        if (discoveryListener != null) return
        _error.value = null
        _discovering.value = true

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                _discovering.value = true
            }

            override fun onDiscoveryStopped(serviceType: String) {
                // Handled in stop()
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoveryListener = null
                _error.value = "mDNS non disponibile ($errorCode)"
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                _servers.value = _servers.value.filterNot {
                    it.serviceName == serviceInfo.serviceName
                }
            }
        }
        discoveryListener = listener
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (error: Exception) {
            discoveryListener = null
            _error.value = error.message ?: "Ricerca mDNS non disponibile"
        }

        startSubnetProbe()
    }

    private fun startSubnetProbe() {
        subnetJob?.cancel()
        subnetJob = scope.launch {
            val localIp = getLocalIpv4() ?: return@launch
            val prefix = localIp.substringBeforeLast('.') + "."
            val myLast = localIp.substringAfterLast('.').toIntOrNull() ?: -1

            // Priority IPs: common server IPs (phone on same network, router, gateway)
            val ipList = mutableListOf<String>()
            for (i in 1..254) {
                if (i != myLast) ipList.add(prefix + i)
            }

            // Chunked fast probe
            val chunks = ipList.chunked(30)
            for (chunk in chunks) {
                if (!isActive) break
                chunk.forEach { ip ->
                    launch {
                        checkHostPort(ip, DEFAULT_PORT)
                    }
                }
                delay(200L)
            }
        }
    }

    private fun checkHostPort(ip: String, port: Int) {
        var conn: HttpURLConnection? = null
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 400)
            }
            // Port is open! Verify it's actually CasaTV
            val urlString = "http://$ip:$port"
            val configUrl = URL("$urlString/api/config")
            conn = (configUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1200
                readTimeout = 1200
                requestMethod = "GET"
            }
            if (conn.responseCode in 200..299) {
                val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = JSONObject(body)
                val serverName = json.optString("name", "CasaTV")
                addServer(
                    DiscoveredServer(
                        serviceName = "probe-$ip",
                        name = serverName,
                        url = urlString,
                    )
                )
                Log.i("CasaTV", "Subnet probe trovato CasaTV su $urlString ($serverName)")
            }
        } catch (_: Exception) {
            // Host is not a CasaTV server
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun getLocalIpv4(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val addresses = networkInterface.inetAddresses
                for (addr in addresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress
                        if (host != null && (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172."))) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) { }
        return null
    }

    @Suppress("DEPRECATION")
    private fun resolve(serviceInfo: NsdServiceInfo) {
        val key = serviceInfo.serviceName
        synchronized(resolving) {
            if (!resolving.add(key)) return
        }
        try {
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    synchronized(resolving) { resolving.remove(key) }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    synchronized(resolving) { resolving.remove(key) }
                    val rawHost = serviceInfo.host?.hostAddress ?: return
                    if (serviceInfo.port <= 0) return
                    val host = if (rawHost.contains(':') && !rawHost.startsWith("[")) {
                        "[$rawHost]"
                    } else {
                        rawHost
                    }
                    val url = "http://$host:${serviceInfo.port}"
                    val displayName = serviceInfo.serviceName
                        .removeSuffix(".")
                        .replace("\\032", " ")
                    val server = DiscoveredServer(
                        serviceName = key,
                        name = displayName.ifBlank { "CasaTV" },
                        url = url,
                    )
                    addServer(server)
                }
            })
        } catch (error: Exception) {
            synchronized(resolving) { resolving.remove(key) }
        }
    }

    fun stop() {
        subnetJob?.cancel()
        subnetJob = null
        val listener = discoveryListener
        if (listener != null) {
            discoveryListener = null
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (_: Exception) { }
        }
        _discovering.value = false
    }

    fun close() {
        stop()
        scope.cancel()
    }

    companion object {
        private const val SERVICE_TYPE = "_casatv._tcp."
        private const val DEFAULT_PORT = 8787
    }
}
