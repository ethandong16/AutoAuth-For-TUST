package com.example.autoauth

import java.net.*

object NetUtil {
    fun getIPv4Address(preferredInterface: String? = null): String? {
        try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return null
            // Try preferred interface first
            if (preferredInterface != null) {
                var preferred: NetworkInterface? = null
                val en = ifaces
                while (en.hasMoreElements()) {
                    val itf = en.nextElement()
                    if (itf.name.equals(preferredInterface, ignoreCase = true)) { preferred = itf; break }
                }
                if (preferred != null && preferred.isUp && !preferred.isLoopback) {
                    val addrs = preferred.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress
                    }
                }
            }
            // If a preferred interface was requested and not found, don't fall back to others
            if (preferredInterface == null) {
                // Fallback: any interface
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    if (!intf.isUp || intf.isLoopback) continue
                    val addrs = intf.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) { }
        return null
    }

    fun selectIPv6Address(preferredInterface: String? = null): Inet6Address? {
        var linkLocalFallback: Inet6Address? = null
        try {
            val allIfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            // Helper to scan one interface
            fun scanInterface(intf: NetworkInterface): Inet6Address? {
                if (!intf.isUp || intf.isLoopback) return null
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet6Address) {
                        if (addr.isLoopbackAddress) continue
                        if (addr.isAnyLocalAddress) continue
                        if (addr.isMulticastAddress) continue
                        if (addr.isLinkLocalAddress) {
                            if (linkLocalFallback == null) linkLocalFallback = addr
                        } else {
                            return addr
                        }
                    }
                }
                return null
            }
            // Try preferred interface first
            if (preferredInterface != null) {
                var preferred: NetworkInterface? = null
                val en = allIfaces
                while (en.hasMoreElements()) {
                    val itf = en.nextElement()
                    if (itf.name.equals(preferredInterface, ignoreCase = true)) { preferred = itf; break }
                }
                val found = preferred?.let { scanInterface(it) }
                if (found != null) return found
            }
            if (preferredInterface == null) {
                // Fallback: scan all
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return linkLocalFallback
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    val found = scanInterface(intf)
                    if (found != null) return found
                }
            }
        } catch (_: Exception) { }
        return linkLocalFallback
    }

    fun formatIPv6ForURL(addr: Inet6Address?): String {
        if (addr == null) return ""
        val b = addr.address ?: return ""
        if (b.size != 16) return ""
        val parts = ArrayList<String>(8)
        var i = 0
        while (i < 16) {
            val hi = b[i].toInt() and 0xff
            val lo = b[i + 1].toInt() and 0xff
            parts.add(String.format("%02x%02x", hi, lo))
            i += 2
        }
        return parts.joinToString(":").replace(":", "%3A")
    }

    fun buildLoginUrl(
        accountEncoded: String,
        passwordEncoded: String,
        ipv4: String,
        ipv6Encoded: String
    ): String {
        val base = "http://10.10.102.50:801/eportal/portal/login"
        val sb = StringBuilder(base)
        sb.append("?callback=dr1005")
        sb.append("&login_method=1")
        sb.append("&user_account=%2C0%2C").append(accountEncoded).append("%40unicom")
        sb.append("&user_password=").append(passwordEncoded)
        sb.append("&wlan_user_ip=").append(ipv4)
        sb.append("&wlan_user_ipv6=").append(ipv6Encoded)
        sb.append("&wlan_user_mac=000000000000")
        sb.append("&wlan_ac_ip=")
        sb.append("&wlan_ac_name=")
        sb.append("&jsVersion=4.1.3")
        sb.append("&terminal_type=1")
        return sb.toString()
    }
}
