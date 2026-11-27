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
import java.util.concurrent.atomic.AtomicBoolean
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
        .connectTimeout(1200, TimeUnit.MILLISECONDS)
        .readTimeout(3500, TimeUnit.MILLISECONDS)
        .build()

    fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    suspend fun discoverFirst(lastKnownUrl: String? = null, timeoutMs: Long = 3500L): DiscoveredServer? =
        withContext(Dispatchers.IO) {
            // 1. Always probe same-device Server (127.0.0.1) & local host interfaces first
            for (host in selfCandidateHosts()) {
                if (probeHealth(host, 8787)) {
                    return@withContext DiscoveredServer("CasaTV Server", host, 8787)
                }
            }

            if (!isOnWifi()) return@withContext null

            // 2. Fast check of last known server URL on current Wi-Fi
            if (!lastKnownUrl.isNullOrBlank()) {
                val parsed = parseHostPort(lastKnownUrl)
                if (parsed != null && probeHealth(parsed.first, parsed.second)) {
                    return@withContext DiscoveredServer("CasaTV Server", parsed.first, parsed.second)
                }
            }

            // 3. Try NSD (_casatv._tcp.) and subnet scan fallback
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

        val finished = AtomicBoolean(false)
        val nsdLock = Any()
        val listener = object : NsdManager.DiscoveryListener {
            private val self = this
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                synchronized(nsdLock) {
                    if (finished.get()) return
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val host = resolved.host?.hostAddress ?: return
                            val port = resolved.port.takeIf { it > 0 } ?: 8787
                            val won = synchronized(nsdLock) { finished.compareAndSet(false, true) }
                            if (!won) return
                            runCatching { nsdManager.stopServiceDiscovery(self) }
                            if (cont.isActive) {
                                cont.resume(DiscoveredServer(resolved.serviceName ?: "CasaTV", host, port))
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                val won = synchronized(nsdLock) { finished.compareAndSet(false, true) }
                if (won && cont.isActive) {
                    cont.resume(null)
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        runCatching {
            nsdManager.discoverServices("_casatv._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            val won = synchronized(nsdLock) { finished.compareAndSet(false, true) }
            if (won && cont.isActive) cont.resume(null)
        }

        cont.invokeOnCancellation {
            synchronized(nsdLock) { finished.set(true) }
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
                        val job = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
                        if (probeHealth(ip, port, job)) DiscoveredServer("CasaTV Server", ip, port) else null
                    }
                }.awaitAll().firstOrNull { it != null }
                if (found != null) return@coroutineScope found
            }
        }
        null
    }

    private fun probeHealth(host: String, port: Int, job: kotlinx.coroutines.Job? = null): Boolean {
        return try {
            val req = Request.Builder().url("http://$host:$port/healthz").build()
            val call = probeClient.newCall(req)
            val handle = job?.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) return false
                    val body = resp.body?.string().orEmpty()
                    body.contains("\"ok\":true", ignoreCase = true) ||
                        body.contains("CasaTV", ignoreCase = true)
                }
            } finally {
                handle?.dispose()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun selfCandidateHosts(): List<String> {
        val hosts = linkedSetOf("127.0.0.1")
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        addr.hostAddress?.takeIf { it.isNotBlank() }?.let { hosts.add(it) }
                    }
                }
            }
        }
        return hosts.toList()
    }

    private fun localIpv4Prefixes(): List<String> {
        val prefixes = linkedSetOf<String>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
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
