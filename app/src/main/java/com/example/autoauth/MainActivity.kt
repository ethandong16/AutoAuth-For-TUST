package com.example.autoauth

import android.Manifest
import android.content.*
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private var statusReceiver: BroadcastReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.subtitle = getString(R.string.app_name_full)

        val etAccount = findViewById<EditText>(R.id.etAccount)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val tvIPv4 = findViewById<TextView>(R.id.tvIPv4)
        val tvIPv6 = findViewById<TextView>(R.id.tvIPv6)
        val tvNetStatus = findViewById<TextView>(R.id.tvNetStatus)
        val tvResult = findViewById<TextView>(R.id.tvResult)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val btnViewLogs = findViewById<Button>(R.id.btnViewLogs)
        val switchBoot = findViewById<android.widget.Switch>(R.id.switchBoot)

        // Load saved credentials
        val prefs = getSharedPreferences("autoauth_prefs", Context.MODE_PRIVATE)
        etAccount.setText(prefs.getString("account", ""))
        etPassword.setText(prefs.getString("password", ""))

        // UI helpers
        fun updateIpsAndPreview() {
            val ipv4Now = NetUtil.getIPv4Address("wlan0") ?: ""
            val ipv6Sel = NetUtil.selectIPv6Address("wlan0")
            val ipv6RawNow = ipv6Sel?.hostAddress?.substringBefore('%') ?: ""
            val ipv6EncNow = NetUtil.formatIPv6ForURL(ipv6Sel)
            tvIPv4.text = "IPv4: $ipv4Now"
            tvIPv6.text = "IPv6: $ipv6RawNow (编码: $ipv6EncNow)"

            val accountEncodedNow = try { URLEncoder.encode(etAccount.text.toString(), "UTF-8") } catch (_: Exception) { etAccount.text.toString() }
            val passwordEncodedNow = try { URLEncoder.encode(etPassword.text.toString(), "UTF-8") } catch (_: Exception) { etPassword.text.toString() }
            val previewUrlNow = NetUtil.buildLoginUrl(accountEncodedNow, passwordEncodedNow, ipv4Now, ipv6EncNow)
            tvResult.text = "当前 URL 预览:\n$previewUrlNow"
        }

        fun checkNetworkImmediate() {
            Thread {
                val status = try {
                    val url = java.net.URL("https://www.baidu.com/")
                    val conn = (url.openConnection() as javax.net.ssl.HttpsURLConnection).apply {
                        connectTimeout = 5000
                        readTimeout = 5000
                        instanceFollowRedirects = false
                        requestMethod = "GET"
                    }
                    val code = conn.responseCode
                    if (code == 200) "已联网" else "未联网"
                } catch (_: Exception) { "未联网" }
                runOnUiThread { tvNetStatus.text = "网络状态: $status" }
            }.start()
        }

        // Auto-save on change
        val watcher = { key: String, value: String ->
            prefs.edit().putString(key, value).apply()
        }
        etAccount.addTextChangedListener(SimpleTextWatcher { watcher("account", it); updateIpsAndPreview() })
        etPassword.addTextChangedListener(SimpleTextWatcher { watcher("password", it); updateIpsAndPreview() })

        // Start service
        btnStart.setOnClickListener {
            val account = etAccount.text?.toString()?.trim().orEmpty()
            val password = etPassword.text?.toString()?.trim().orEmpty()
            if (account.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请输入账号和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save once more
            prefs.edit().putString("account", account).putString("password", password).apply()

            // Request notification permission on Android 13+
            if (Build.VERSION.SDK_INT >= 33) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
                    return@setOnClickListener
                }
            }

            val i = Intent(this, AuthService::class.java)
            ContextCompat.startForegroundService(this, i)
            Toast.makeText(this, "已启动后台任务", Toast.LENGTH_SHORT).show()
        }

        // Stop service
        btnStop.setOnClickListener {
            val i = Intent(this, AuthService::class.java)
            stopService(i)
            Toast.makeText(this, "已停止后台任务", Toast.LENGTH_SHORT).show()
        }

        btnViewLogs.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        // Service status updates
        val statusFilter = IntentFilter("com.example.autoauth.STATUS")
        statusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                val net = intent.getStringExtra("networkStatus")
                val url = intent.getStringExtra("lastUrl")
                val summary = intent.getStringExtra("lastSummary")
                val running = intent.getBooleanExtra("serviceRunning", false)
                tvNetStatus.text = "网络状态: ${net ?: "未知"}"
                tvResult.text = buildString {
                    if (!url.isNullOrEmpty()) append("最近请求:\n").append(url).append('\n')
                    if (!summary.isNullOrEmpty()) append("结果: \n").append(summary)
                }
                prefs.edit().putBoolean("service_running", running).apply()
            }
        }
        // Android 14+ (targetSdk >= 34) requires specifying export state
        registerReceiver(statusReceiver, statusFilter, Context.RECEIVER_NOT_EXPORTED)

        // Boot auto-start toggle
        switchBoot.isChecked = prefs.getBoolean("boot_autostart", true)
        switchBoot.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("boot_autostart", isChecked).apply()
            Toast.makeText(this, if (isChecked) "已开启开机自启" else "已关闭开机自启", Toast.LENGTH_SHORT).show()
        }

        // Listen for network changes to refresh IP/URL in real time
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { runOnUiThread { updateIpsAndPreview() } }
            override fun onLost(network: Network) { runOnUiThread { updateIpsAndPreview() } }
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) { runOnUiThread { updateIpsAndPreview() } }
        }
        try { cm.registerDefaultNetworkCallback(networkCallback!!) } catch (_: Exception) {}

        // Initial refresh and immediate network check
        updateIpsAndPreview()
        checkNetworkImmediate()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_author, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_author) return true
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (statusReceiver != null) {
            unregisterReceiver(statusReceiver)
            statusReceiver = null
        }
        if (networkCallback != null) {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            try { cm.unregisterNetworkCallback(networkCallback!!) } catch (_: Exception) {}
            networkCallback = null
        }
    }
}

// SimpleTextWatcher
private class SimpleTextWatcher(val onChanged: (String) -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) { onChanged(s?.toString().orEmpty()) }
}
