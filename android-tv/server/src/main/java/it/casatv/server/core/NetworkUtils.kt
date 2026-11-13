package it.casatv.server.core

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

data class LanAddress(val label: String, val address: String)

object NetworkUtils {
    /** Best-effort list of IPv4 LAN addresses (Wi-Fi, Ethernet, hotspot). */
    fun addresses(): List<LanAddress> {
        val out = mutableListOf<LanAddress>()
        try {
            for (iface in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp || iface.isLoopback) continue
                for (address in Collections.list(iface.inetAddresses)) {
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        out += LanAddress(iface.name, address.hostAddress ?: continue)
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore: no network permission or interface disappeared.
        }
        return out.distinctBy { it.address }
            .sortedWith(compareBy({ if (it.label.startsWith("wlan")) 0 else 1 }, { it.address }))
    }

    fun primaryIp(): String? = addresses().firstOrNull()?.address

    fun baseUrl(ip: String?, port: Int): String =
        "http://${ip ?: "127.0.0.1"}:$port"
}
