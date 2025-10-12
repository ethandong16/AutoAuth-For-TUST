package com.example.autoauth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("autoauth_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("boot_autostart", true)
            if (enabled) {
                try {
                    val i = Intent(context, AuthService::class.java)
                    ContextCompat.startForegroundService(context, i)
                    LogUtil.append(context, "开机自启: 已启动服务")
                } catch (e: Exception) {
                    LogUtil.append(context, "开机自启失败: ${e.message}")
                }
            } else {
                LogUtil.append(context, "开机自启: 已禁用")
            }
        }
    }
}
