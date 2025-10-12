package com.example.autoauth

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection
import java.net.Inet6Address
import java.net.URL
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class AuthService : Service() {
    private val channelId = "autoauth_channel"
    private val scheduler = ScheduledThreadPoolExecutor(1)
    private var task: ScheduledFuture<*>? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notif = buildNotification("AutoAuth 运行中")
        startForeground(1, notif)
        schedule()
        getSharedPreferences("autoauth_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("service_running", true).apply()
        sendStatus("未知", null, null, true)
        LogUtil.append(this, "服务已启动")
    }

    override fun onDestroy() {
        super.onDestroy()
        task?.cancel(true)
        scheduler.shutdownNow()
        getSharedPreferences("autoauth_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("service_running", false).apply()
        sendStatus("停止", null, null, false)
        LogUtil.append(this, "服务已停止")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            LogUtil.append(this, "接收到停止指令，正在停止服务")
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun schedule() {
        task = scheduler.scheduleWithFixedDelay({
            try {
                val netStatus = checkNetwork()
                val url = buildUrl()
                val result = performGet(url)
                LogUtil.append(this, "网络=${netStatus} | GET结果=${result.take(120).replace('\n',' ')}")
                sendStatus(netStatus, url, result.lines().firstOrNull() ?: result, true)
            } catch (e: Exception) {
                LogUtil.append(this, "任务异常: ${e.message}")
                sendStatus("异常", null, e.message ?: "", true)
            }
        }, 0, 10, TimeUnit.SECONDS)
    }

    private fun checkNetwork(): String {
        return try {
            val url = URL("https://www.baidu.com/")
            val conn = (url.openConnection() as HttpsURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                instanceFollowRedirects = false
                requestMethod = "GET"
            }
            val code = conn.responseCode
            if (code == 200) "已联网" else "未联网"
        } catch (_: Exception) { "未联网" }
    }

    private fun buildUrl(): String {
        val prefs = getSharedPreferences("autoauth_prefs", Context.MODE_PRIVATE)
        val account = prefs.getString("account", "") ?: ""
        val password = prefs.getString("password", "") ?: ""
        val ipv4 = NetUtil.getIPv4Address() ?: ""
        val ipv6 = NetUtil.selectIPv6Address()
        val ipv6Encoded = NetUtil.formatIPv6ForURL(ipv6)
        val encodedAccount = try { java.net.URLEncoder.encode(account, "UTF-8") } catch (_: Exception) { account }
        val encodedPassword = try { java.net.URLEncoder.encode(password, "UTF-8") } catch (_: Exception) { password }
        return NetUtil.buildLoginUrl(encodedAccount, encodedPassword, ipv4, ipv6Encoded)
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
            val body = try {
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                stream?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (_: Exception) { "" }
            val summary = parsePortalSummary(body)
            if (summary.isNotEmpty()) "HTTP $code | $summary" else "HTTP $code"
        } catch (e: Exception) {
            "请求失败: ${e.message}"
        }
    }

    private fun parsePortalSummary(body: String): String {
        if (body.isBlank()) return ""
        // Try to extract JSON inside callback dr1005(...)
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        if (start >= 0 && end > start) {
            val jsonStr = body.substring(start, end + 1)
            try {
                val obj = org.json.JSONObject(jsonStr)
                val result = obj.optString("result", obj.optString("ret_code"))
                val msg = obj.optString("msg", obj.optString("message"))
                return buildString {
                    if (result.isNotEmpty()) append("result=").append(result)
                    if (msg.isNotEmpty()) {
                        if (isNotEmpty()) append(", ")
                        append("msg=").append(msg)
                    }
                }
            } catch (_: Exception) { /* ignore */ }
        }
        // Fallback: heuristics
        return when {
            body.contains("success", ignoreCase = true) -> "登录成功"
            body.contains("ok", ignoreCase = true) -> "登录成功(可能)"
            body.contains("error", ignoreCase = true) -> "登录失败"
            body.contains("fail", ignoreCase = true) -> "登录失败"
            else -> ""
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(channelId, "AutoAuth", NotificationManager.IMPORTANCE_LOW)
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, AuthService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AutoAuth For TUST")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pi)
            .setOngoing(true)
            // Android 14/15 can crash if action icon is 0
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPi)
            .build()
    }

    private fun sendStatus(net: String, url: String?, summary: String?, running: Boolean) {
        val i = Intent("com.example.autoauth.STATUS")
        i.putExtra("networkStatus", net)
        i.putExtra("lastUrl", url)
        i.putExtra("lastSummary", summary)
        i.putExtra("serviceRunning", running)
        sendBroadcast(i)
    }
}

private const val ACTION_STOP = "com.example.autoauth.ACTION_STOP"
