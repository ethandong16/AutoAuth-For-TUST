package com.example.autoauth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("autoauth_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("boot_autostart", true)
            if (enabled) {
                // 延迟执行，避免 ForegroundServiceStartNotAllowedException
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val i = Intent(context, AuthService::class.java)
                        // ⚠️ 改成普通后台服务启动
                        context.startService(i)
                        LogUtil.append(context, "开机自启: 延迟启动服务成功")
                    } catch (e: Exception) {
                        LogUtil.append(context, "开机自启失败: ${e.message}")
                    }
                }, 8000) // 延迟 8 秒启动，可自行调整
            } else {
                LogUtil.append(context, "开机自启: 已禁用")
            }
        }
    }
}
