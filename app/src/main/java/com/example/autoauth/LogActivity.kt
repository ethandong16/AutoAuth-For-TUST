package com.example.autoauth

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem

class LogActivity : AppCompatActivity() {
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
        val btn = Button(this)
        btn.text = "刷新"
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16,16,16,16)
            addView(authorTv, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(btn, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(ScrollView(this@LogActivity).apply { addView(tv) }, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            ))
        }
        setContentView(container)

        fun refresh() { tv.text = LogUtil.readAll(this) }
        btn.setOnClickListener { refresh() }
        refresh()
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
