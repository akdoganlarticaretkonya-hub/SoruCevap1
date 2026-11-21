package com.stokcevap.app

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Google Sheets'ten herkese açık tabloyu CSV olarak indirir ve ayrıştırır. */
object Sheet {

    private val invisible = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\uFEFF]")
    private val spaces = Regex("\\s+")

    /** Karşılaştırma için: büyük/küçük harf, fazla boşluk ve I/İ/ı farkı yok sayılır. */
    fun norm(s: String): String =
        s.replace(invisible, "")
            .replace(' ', ' ')
            .trim()
            .replace(spaces, " ")
            .replace('İ', 'i').replace('I', 'i').replace('ı', 'i')
            .lowercase(Locale.ROOT)

    fun sheetId(url: String): String? =
        Regex("/d/([a-zA-Z0-9_-]+)").find(url)?.groupValues?.get(1)

    fun download(url: String, sheetName: String): String {
        val id = sheetId(url)
            ?: throw IllegalArgumentException("Bağlantıda tablo kimliği bulunamadı")
        val u = "https://docs.google.com/spreadsheets/d/$id/gviz/tq?tqx=out:csv&sheet=" +
            URLEncoder.encode(sheetName, "UTF-8")
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 20000
        c.readTimeout = 30000
        c.instanceFollowRedirects = true
        try {
            if (c.responseCode != 200) {
                throw RuntimeException(
                    "HTTP ${c.responseCode}: tablo \"Bağlantıya sahip herkes görüntüleyebilir\" olmalı"
                )
            }
            val t = c.inputStream.bufferedReader(Charsets.UTF_8).readText()
            if (t.trimStart().startsWith("<")) {
                throw RuntimeException("Tablo herkese açık değil ya da sayfa adı yanlış")
            }
            return t
        } finally {
            c.disconnect()
        }
    }

    fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = text.length
        while (i < n) {
            val ch = text[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < n && text[i + 1] == '"') {
                        sb.append('"'); i++
                    } else inQuotes = false
                } else sb.append(ch)
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> { row.add(sb.toString()); sb.setLength(0) }
                    '\r' -> {}
                    '\n' -> {
                        row.add(sb.toString()); sb.setLength(0)
                        rows.add(row); row = ArrayList()
                    }
                    else -> sb.append(ch)
                }
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) {
            row.add(sb.toString()); rows.add(row)
        }
        return rows
    }
}
