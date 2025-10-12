package com.example.autoauth

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogUtil {
    private const val DIR = "autoauth"
    private const val FILE = "log.txt"
    private const val ACTION_LOG_UPDATED = "com.example.autoauth.LOG_UPDATED"
    private const val DEFAULT_MAX_BYTES = 128 * 1024 // 128KB
    private const val DEFAULT_MAX_LINES = 800
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
            // broadcast log update for real-time UI refresh
            try {
                ctx.sendBroadcast(android.content.Intent(ACTION_LOG_UPDATED))
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    fun readAll(ctx: Context): String {
        return readLatest(ctx, DEFAULT_MAX_BYTES, DEFAULT_MAX_LINES)
    }

    fun readLatest(ctx: Context, maxBytes: Int = DEFAULT_MAX_BYTES, maxLines: Int = DEFAULT_MAX_LINES): String {
        return try {
            val f = File(File(ctx.filesDir, DIR), FILE)
            if (!f.exists()) return "(无日志)"
            val len = f.length()
            val start = if (len > maxBytes) len - maxBytes else 0
            val raf = java.io.RandomAccessFile(f, "r")
            raf.seek(start)
            val bytes = ByteArray((len - start).toInt())
            raf.readFully(bytes)
            raf.close()
            val text = bytes.toString(Charsets.UTF_8)
            val lines = text.lines()
            if (lines.size > maxLines) lines.takeLast(maxLines).joinToString("\n") else text
        } catch (e: Exception) {
            "读取日志失败: ${e.message}"
        }
    }
}

