package com.example.autoauth

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogUtil {
    private const val DIR = "autoauth"
    private const val FILE = "log.txt"
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun append(ctx: Context, line: String) {
        try {
            val dir = File(ctx.filesDir, DIR)
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, FILE)
            // rotate if > 1MB
            if (f.exists() && f.length() > 1_000_000) {
                val bak = File(dir, "log.txt.1")
                if (bak.exists()) bak.delete()
                f.renameTo(bak)
            }
            val ts = sdf.format(Date())
            f.appendText("[$ts] $line\n")
        } catch (_: Exception) {}
    }

    fun readAll(ctx: Context): String {
        return try {
            val f = File(File(ctx.filesDir, DIR), FILE)
            if (!f.exists()) "(无日志)" else f.readText()
        } catch (e: Exception) { "读取日志失败: ${e.message}" }
    }
}

