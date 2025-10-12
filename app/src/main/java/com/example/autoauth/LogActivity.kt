package com.example.autoauth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem

class LogActivity : AppCompatActivity() {
    private var logReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.subtitle = getString(R.string.app_name_full)

        // Author label (copyright/author identification)
        val authorTv = TextView(this).apply {
            text = getString(R.string.author)
            textSize = 10f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.END
        }

        val tv = TextView(this)
        tv.textSize = 12f
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16,16,16,16)
            addView(authorTv, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            val scroll = ScrollView(this@LogActivity).apply { addView(tv) }
            addView(scroll, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            ))
        }
        setContentView(container)

        fun scrollToBottom() {
            val sv = container.getChildAt(container.childCount - 1) as? ScrollView
            sv?.post { sv.fullScroll(View.FOCUS_DOWN) }
        }

        fun refresh() {
            tv.text = LogUtil.readAll(this)
            scrollToBottom()
        }
        // initial load
        refresh()

        // live updates via broadcast
        val filter = IntentFilter("com.example.autoauth.LOG_UPDATED")
        logReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refresh()
            }
        }
        registerReceiver(logReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (logReceiver != null) {
            unregisterReceiver(logReceiver)
            logReceiver = null
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_author, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_author) return true
        return super.onOptionsItemSelected(item)
    }
}
