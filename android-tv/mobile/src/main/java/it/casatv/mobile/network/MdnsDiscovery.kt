package it.casatv.mobile.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class DiscoveredServer(
    val name: String,
    val host: String,
    val port: Int,
) {
    val baseUrl: String get() = "http://$host:$port"
}

class MdnsDiscovery(private val context: Context) {

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(700, TimeUnit.MILLISECONDS)
        .readTimeout(1200, TimeUnit.MILLISECONDS)
        .build()

    fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    suspend fun discoverFirst(lastKnownUrl: String? = null, timeoutMs: Long = 3500L): DiscoveredServer? =
        withContext(Dispatchers.IO) {
            if (!isOnWifi()) return@withContext null

            // 1. Fast check of last known server URL on current Wi-Fi
            if (!lastKnownUrl.isNullOrBlank()) {
                val parsed = parseHostPort(lastKnownUrl)
                if (parsed != null && probeHealth(parsed.first, parsed.second)) {
                    return@withContext DiscoveredServer("CasaTV Server", parsed.first, parsed.second)
                }
            }

            // 2. Try NSD (_casatv._tcp.) and subnet scan in parallel
            val nsd = withTimeoutOrNull(timeoutMs) { discoverViaNsd() }
            if (nsd != null) return@withContext nsd

            scanLocalSubnet(8787)
        }

    private suspend fun discoverViaNsd(): DiscoveredServer? = suspendCancellableCoroutine { cont ->
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsdManager == null) {
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }

        var finished = false
        val listener = object : NsdManager.DiscoveryListener {
            private val self = this
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (finished) return
                @Suppress("DEPRECATION")
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        if (finished) return
                        @Suppress("DEPRECATION")
                        val host = resolved.host?.hostAddress ?: return
                        val port = resolved.port.takeIf { it > 0 } ?: 8787
                        finished = true
                        runCatching { nsdManager.stopServiceDiscovery(self) }
                        if (cont.isActive) {
                            cont.resume(DiscoveredServer(resolved.serviceName ?: "CasaTV", host, port))
                        }
                    }
                })
            }

            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (!finished && cont.isActive) {
                    finished = true
                    cont.resume(null)
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        runCatching {
            nsdManager.discoverServices("_casatv._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            if (cont.isActive) cont.resume(null)
        }

        cont.invokeOnCancellation {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
    }

    private suspend fun scanLocalSubnet(port: Int): DiscoveredServer? = coroutineScope {
        val prefixes = localIpv4Prefixes()
        for (prefix in prefixes) {
            val candidates = (1..254).map { hostByte -> "$prefix.$hostByte" }
            for (chunk in candidates.chunked(32)) {
                val found = chunk.map { ip ->
                    async {
                        if (probeHealth(ip, port)) DiscoveredServer("CasaTV Server", ip, port) else null
                    }
                }.awaitAll().firstOrNull { it != null }
                if (found != null) return@coroutineScope found
            }
        }
        null
    }

    private fun probeHealth(host: String, port: Int): Boolean {
        return try {
            val req = Request.Builder().url("http://$host:$port/healthz").build()
            probeClient.newCall(req).execute().use { resp ->
                resp.isSuccessful && (resp.body?.string()?.contains("CasaTV", ignoreCase = true) == true)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun localIpv4Prefixes(): List<String> {
        val prefixes = linkedSetOf<String>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        val parts = ip.split('.')
                        if (parts.size == 4) {
                            prefixes.add("${parts[0]}.${parts[1]}.${parts[2]}")
                        }
                    }
                }
            }
        }
        return prefixes.toList()
    }

    private fun parseHostPort(url: String): Pair<String, Int>? {
        val clean = url.removePrefix("http://").removePrefix("https://").substringBefore('/')
        val parts = clean.split(':')
        val host = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 8787
        return host to port
    }
}
