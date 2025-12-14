package com.example.autoauth

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection
import java.net.URL
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class AuthService : Service() {
    private val channelId = "autoauth_channel"
    private val scheduler = ScheduledThreadPoolExecutor(1)
    private var task: ScheduledFuture<*>? = null
    private var startedForeground = false

    override fun onCreate() {
        super.onCreate()
        createChannel()

        // ✅ 延迟进入前台模式，避免 ForegroundServiceStartNotAllowedException
        Handler(Looper.getMainLooper()).postDelayed({
            if (!startedForeground) {
                val notif = buildNotification("AutoAuth 运行中")
                try {
                    startForeground(1, notif)
                    startedForeground = true
                    LogUtil.append(this, "前台服务已启动（延迟）")
                } catch (e: Exception) {
                    LogUtil.append(this, "前台启动失败: ${e.message}")
                }
            }
        }, 8000) // 延迟 8 秒，可视情况调整

        // 立即启动核心逻辑
        schedule()
        getSharedPreferences("autoauth_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("service_running", true).apply()
        sendStatus("未知", null, null, true)
        LogUtil.append(this, "服务已创建并开始调度")
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

    // ================= 网络任务逻辑 =================

    private fun schedule() {
        task = scheduler.scheduleWithFixedDelay({
            try {
                val netStatus = checkNetwork()
                val (url, result) = performGetWithFreshIPs()
                LogUtil.append(this, "网络=$netStatus | GET结果=${result.take(120).replace('\n',' ')}")
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
            if (conn.responseCode == 200) "已联网" else "未联网"
        } catch (_: Exception) { "未联网" }
    }

    private fun buildUrl(): String {
        val prefs = getSharedPreferences("autoauth_prefs", Context.MODE_PRIVATE)
        val account = prefs.getString("account", "") ?: ""
        val password = prefs.getString("password", "") ?: ""
        val customIPv4 = prefs.getString("custom_ipv4", "") // 可能为空
        val customIPv6 = prefs.getString("custom_ipv6", "") // 可能为空

        val ipv4 = if (customIPv4 != null) {
            // 如果用户动过输入框，使用输入框的值（哪怕是空字符串）
            customIPv4
        } else {
            // 如果没有保存过（首次运行等），回退到自动获取
             NetUtil.getIPv4Address("wlan0") ?: ""
        }

        val ipv6Encoded = if (customIPv6 != null) {
            // 用户输入了值（包括空字符串），则直接使用该值并编码
            try { java.net.URLEncoder.encode(customIPv6, "UTF-8") } catch (_: Exception) { customIPv6 }
        } else {
             // 没有保存过，自动获取
             NetUtil.formatIPv6ForURL(NetUtil.selectIPv6Address("wlan0"))
        }
        
        // 注意：上面逻辑稍微修正一下以完全符合“留空就留空”。
        // 实际上 prefs.getString 如果没存过会返回 default。
        // MainActivity里已经设置了默认值 "10.59.14.49" 和 ""。
        // 所以这里只要取出来直接用即可。如果是空字符串，就是空字符串，不要去自动获取。
        
        // 修正逻辑：只有当它真的是 null 的时候才去自动获取（理论上经过 MainActivity 保存后不会是 null，但为了健壮性）
        // 但用户的要求是“允许自定义...并且允许留空”。如果用户把输入框清空了，那就是空字符串。
        // 用户说“不要自动获取”，意味着如果输入框是空的，URL参数里就该是空的。
        
        // 重新梳理：
        // 1. 如果 custom_ipv4 是 ""，那么 ipv4 就是 ""。
        // 2. 如果 custom_ipv4 是 "1.2.3.4"，那么 ipv4 就是 "1.2.3.4"。
        // 只有一种情况可能需要自动获取：用户从来没运行过UI，Service直接起来了（例如开机自启）。
        // 但MainActivity里保存了默认值。
        // 如果用户清空了输入框，prefs里就是""。此时不应该自动获取。
        
        val finalIPv4 = if (customIPv4 == null) {
             NetUtil.getIPv4Address("wlan0") ?: ""
        } else {
             customIPv4
        }

        val finalIPv6Encoded = if (customIPv6 == null) {
             NetUtil.formatIPv6ForURL(NetUtil.selectIPv6Address("wlan0"))
        } else {
             try { java.net.URLEncoder.encode(customIPv6, "UTF-8") } catch (_: Exception) { customIPv6 }
        }

        val encodedAccount = try { java.net.URLEncoder.encode(account, "UTF-8") } catch (_: Exception) { account }
        val encodedPassword = try { java.net.URLEncoder.encode(password, "UTF-8") } catch (_: Exception) { password }
        return NetUtil.buildLoginUrl(encodedAccount, encodedPassword, finalIPv4, finalIPv6Encoded)
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

    private fun performGetWithFreshIPs(): Pair<String, String> {
        val url = buildUrl()
        val result = performGet(url)
        return url to result
    }

    private fun parsePortalSummary(body: String): String {
        if (body.isBlank()) return ""
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
            } catch (_: Exception) {}
        }
        return when {
            body.contains("success", true) -> "登录成功"
            body.contains("ok", true) -> "登录成功(可能)"
            body.contains("error", true) -> "登录失败"
            body.contains("fail", true) -> "登录失败"
            else -> ""
        }
    }

    // ================= 通知与状态广播 =================

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
