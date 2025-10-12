package com.example.autoauth

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val ioExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etAccount = findViewById<EditText>(R.id.etAccount)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val tvIPv4 = findViewById<TextView>(R.id.tvIPv4)
        val tvIPv6 = findViewById<TextView>(R.id.tvIPv6)
        val tvResult = findViewById<TextView>(R.id.tvResult)
        val btnSend = findViewById<Button>(R.id.btnSend)

        val ipv4 = getIPv4Address() ?: ""
        val selectedIPv6 = selectIPv6Address()
        val ipv6Raw = selectedIPv6?.hostAddress?.substringBefore('%') ?: ""
        val ipv6Encoded = formatIPv6ForURL(selectedIPv6)

        tvIPv4.text = "IPv4: $ipv4"
        tvIPv6.text = "IPv6: $ipv6Raw (编码后: $ipv6Encoded)"

        btnSend.setOnClickListener {
            val account = etAccount.text?.toString()?.trim().orEmpty()
            val password = etPassword.text?.toString()?.trim().orEmpty()

            if (account.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请输入账号和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val encodedAccount = try {
                URLEncoder.encode(account, "UTF-8")
            } catch (e: Exception) { account }

            val encodedPassword = try {
                URLEncoder.encode(password, "UTF-8")
            } catch (e: Exception) { password }

            val finalUrl = buildLoginUrl(
                accountEncoded = encodedAccount,
                passwordEncoded = encodedPassword,
                ipv4 = ipv4,
                ipv6Encoded = ipv6Encoded
            )

            tvResult.text = "请求 URL:\n$finalUrl"

            ioExecutor.execute {
                val result = performGet(finalUrl)
                runOnUiThread {
                    tvResult.text = "请求 URL:\n$finalUrl\n\n结果:\n$result"
                }
            }
        }
    }

    private fun buildLoginUrl(
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

    private fun performGet(urlStr: String): String {
        return try {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { s ->
                BufferedReader(InputStreamReader(s)).use { br ->
                    buildString {
                        var line: String?
                        var count = 0
                        while (br.readLine().also { line = it } != null && count < 2000) {
                            append(line).append('\n')
                            count += line!!.length
                        }
                    }
                }
            } ?: ""
            "HTTP $code\n" + body
        } catch (e: Exception) {
            "请求失败: ${e.message}"
        }
    }

    private fun getIPv4Address(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) { }
        return null
    }

    private fun selectIPv6Address(): Inet6Address? {
        var linkLocalFallback: Inet6Address? = null
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (!intf.isUp || intf.isLoopback) continue
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
                            return addr // Prefer global/ULA
                        }
                    }
                }
            }
        } catch (_: Exception) { }
        return linkLocalFallback
    }

    // Replicates the provided Go logic: expand to 8 groups of 4 hex digits (lowercase), then replace ':' with '%3A'
    private fun formatIPv6ForURL(addr: Inet6Address?): String {
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
}
