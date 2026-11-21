package com.stokcevap.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.os.Handler
import android.os.Looper

class MainActivity : Activity() {

    private lateinit var url: EditText
    private lateinit var sheet: EditText
    private lateinit var fallback: EditText
    private lateinit var enabled: CheckBox
    private lateinit var silent: CheckBox
    private lateinit var status: TextView
    private lateinit var testIn: EditText
    private lateinit var testOut: TextView
    private val ui = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refreshStatus()
            ui.postDelayed(this, 3000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(t: String) = TextView(this).apply {
        text = t
        textSize = 14f
        setPadding(0, dp(14), 0, dp(2))
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        val scroll = ScrollView(this)
        scroll.addView(root, ViewGroup.LayoutParams(-1, -2))
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "Stok Cevap"
            textSize = 22f
        })

        enabled = CheckBox(this).apply { text = "Otomatik cevap açık" }
        root.addView(enabled)

        root.addView(label("Google Sheets bağlantısı (Bağlantıya sahip herkes görüntüleyebilir)"))
        url = EditText(this).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            hint = "https://docs.google.com/spreadsheets/d/..."
            setSingleLine(false)
        }
        root.addView(url)

        root.addView(label("Sayfa adı"))
        sheet = EditText(this).apply { setSingleLine(true) }
        root.addView(sheet)

        root.addView(label("Eşleşme yoksa gönderilecek otomatik cevap (boşsa sessiz kalır)"))
        fallback = EditText(this).apply {
            minLines = 2
            gravity = android.view.Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        root.addView(fallback)

        silent = CheckBox(this).apply { text = "Gruplarda eşleşme yoksa sessiz kal (önerilir)" }
        root.addView(silent)

        root.addView(button("Kaydet ve tabloyu şimdi yenile") {
            save()
            Thread {
                Store.ensureFresh(this, force = true)
                ui.post { refreshStatus() }
            }.start()
        })
        root.addView(button("1) Bildirim erişimini aç") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        root.addView(button("2) Pil kısıtlamasını kaldır") {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        })

        root.addView(label("Deneme: bir soru yazın, cevabını görün"))
        testIn = EditText(this).apply { setSingleLine(true) }
        root.addView(testIn)
        root.addView(button("Dene") {
            save()
            val q = testIn.text.toString()
            Thread {
                Store.ensureFresh(this)
                val a = Store.lookup(q)
                ui.post {
                    testOut.text = if (a != null) "Cevap: $a" else "Eşleşme yok"
                }
            }.start()
        })
        testOut = TextView(this).apply { textSize = 16f }
        root.addView(testOut)

        root.addView(label("Durum"))
        status = TextView(this).apply { setTextIsSelectable(true) }
        root.addView(status)

        url.setText(Store.url(this))
        sheet.setText(Store.sheetName(this))
        fallback.setText(Store.fallback(this))
        enabled.isChecked = Store.enabled(this)
        silent.isChecked = Store.silentGroups(this)
    }

    private fun save() {
        Store.prefs(this).edit()
            .putString("url", url.text.toString().trim())
            .putString("sheet", sheet.text.toString().trim().ifEmpty { "Sayfa1" })
            .putString("fallback", fallback.text.toString())
            .putBoolean("enabled", enabled.isChecked)
            .putBoolean("silentGroups", silent.isChecked)
            .apply()
    }

    private fun listenerOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return s != null && s.contains(packageName)
    }

    private fun refreshStatus() {
        val sb = StringBuilder()
        sb.append("Bildirim erişimi: ").append(if (listenerOn()) "AÇIK" else "KAPALI").append('\n')
        sb.append("Tablodaki satır: ").append(Store.map.size).append('\n')
        val t = Store.lastOk
        if (t > 0) {
            val min = (System.currentTimeMillis() - t) / 60000
            sb.append("Son yenileme: ").append(min).append(" dk önce (her 15 dk'da bir yenilenir)\n")
        }
        Store.lastError?.let { sb.append("Son hata: ").append(it).append('\n') }
        sb.append("\nSon olaylar:\n").append(Store.logText())
        status.text = sb.toString()
    }

    override fun onResume() {
        super.onResume()
        ui.post(ticker)
    }

    override fun onPause() {
        ui.removeCallbacks(ticker)
        save()
        super.onPause()
    }
}
