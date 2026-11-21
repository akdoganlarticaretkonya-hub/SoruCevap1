package com.stokcevap.app

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ayarlar, tablo önbelleği (en fazla 15 dk eski) ve kısa olay günlüğü. */
object Store {
    const val TTL_MS = 15 * 60 * 1000L
    private const val RETRY_MS = 60 * 1000L

    @Volatile var map: Map<String, String> = emptyMap()
    @Volatile var lastOk = 0L
    @Volatile var lastError: String? = null
    @Volatile private var lastTry = 0L
    @Volatile private var loaded = false

    private val logs = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun enabled(c: Context) = prefs(c).getBoolean("enabled", true)
    fun url(c: Context) = prefs(c).getString("url", "") ?: ""
    fun sheetName(c: Context) = prefs(c).getString("sheet", "Sayfa1") ?: "Sayfa1"
    fun fallback(c: Context) = prefs(c).getString("fallback", "") ?: ""
    fun silentGroups(c: Context) = prefs(c).getBoolean("silentGroups", true)

    @Synchronized
    fun log(s: String) {
        logs.addFirst("${fmt.format(Date())}  $s")
        while (logs.size > 25) logs.removeLast()
    }

    @Synchronized
    fun logText(): String = logs.joinToString("\n")

    private fun build(rows: List<List<String>>): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        for (r in rows) {
            if (r.isEmpty()) continue
            val k = Sheet.norm(r[0])
            if (k.isEmpty()) continue
            val v = r.getOrElse(1) { "" }.trim()
            if (!m.containsKey(k)) m[k] = v
        }
        return m
    }

    /** Önbellek eskiyse (15 dk) tabloyu yeniler. Başarısız olursa eski veriyi kullanır. */
    @Synchronized
    fun ensureFresh(c: Context, force: Boolean = false): Boolean {
        val ctx = c.applicationContext
        if (!loaded) {
            loaded = true
            try {
                val f = File(ctx.filesDir, "cache.csv")
                if (f.exists()) map = build(Sheet.parseCsv(f.readText()))
            } catch (_: Exception) {
            }
        }
        val now = System.currentTimeMillis()
        if (!force) {
            if (map.isNotEmpty() && now - lastOk < TTL_MS) return true
            if (lastTry > 0 && now - lastTry < RETRY_MS) return map.isNotEmpty()
        }
        lastTry = now
        val url = url(ctx)
        if (url.isBlank()) {
            lastError = "Tablo bağlantısı girilmemiş"
            return map.isNotEmpty()
        }
        try {
            val text = Sheet.download(url, sheetName(ctx))
            val m = build(Sheet.parseCsv(text))
            map = m
            lastOk = now
            lastError = null
            File(ctx.filesDir, "cache.csv").writeText(text)
            log("Tablo yenilendi: ${m.size} satır")
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            log("Yenileme hatası: $lastError")
        }
        return map.isNotEmpty()
    }

    fun lookup(question: String): String? {
        val v = map[Sheet.norm(question)] ?: return null
        return v.ifBlank { null }
    }
}
