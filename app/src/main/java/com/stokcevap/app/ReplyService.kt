package com.stokcevap.app

import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.Executors

/**
 * WhatsApp / WhatsApp Business bildirimlerini okur. Mesaj tablodaki bir "soru" ile
 * aynıysa, bildirimin "Yanıtla" özelliğiyle tablodaki cevabı gönderir.
 * Başka hiçbir şey yapmaz.
 */
class ReplyService : NotificationListenerService() {

    private val exec = Executors.newSingleThreadExecutor()
    private val seen = LinkedHashSet<String>()
    private val recentReplies = ArrayList<Pair<String, Long>>()
    private val fallbackAt = HashMap<String, Long>()

    private val packages = setOf("com.whatsapp", "com.whatsapp.w4b")

    override fun onListenerConnected() {
        Store.log("Dinleme başladı")
        exec.execute { Store.ensureFresh(this) }
    }

    override fun onListenerDisconnected() {
        Store.log("Dinleme koptu, yeniden bağlanılıyor")
        try {
            requestRebind(ComponentName(this, ReplyService::class.java))
        } catch (_: Exception) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in packages) return
        if (!Store.enabled(this)) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        exec.execute {
            try {
                handle(sbn)
            } catch (e: Exception) {
                Store.log("Hata: ${e.message}")
            }
        }
    }

    private class Msg(val text: String, val time: Long)

    @Suppress("DEPRECATION")
    private fun readMessages(sbn: StatusBarNotification): List<Msg> {
        val extras = sbn.notification.extras
        val out = ArrayList<Msg>()
        val arr = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (arr != null) {
            for (p in arr) {
                val b = p as? Bundle ?: continue
                val t = b.getCharSequence("text")?.toString() ?: continue
                out.add(Msg(t, b.getLong("time", sbn.postTime)))
            }
        }
        if (out.isEmpty()) {
            val t = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            if (!t.isNullOrBlank()) out.add(Msg(t, sbn.postTime))
        }
        return out
    }

    private fun handle(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val title = (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE) ?: "").toString()
        val chatKey = sbn.packageName + "|" + title
        val now = System.currentTimeMillis()

        val fresh = ArrayList<Msg>()
        for (m in readMessages(sbn)) {
            if (now - m.time > 120_000) continue
            val key = chatKey + "|" + m.time + "|" + m.text
            if (!seen.add(key)) continue
            if (seen.size > 600) seen.remove(seen.first())
            val n = Sheet.norm(m.text)
            recentReplies.removeAll { now - it.second > 180_000 }
            if (recentReplies.any { it.first == n }) continue // kendi cevabımız
            fresh.add(m)
        }
        if (fresh.isEmpty()) return

        Store.ensureFresh(this)

        for (m in fresh) {
            val answer = Store.lookup(m.text)
            val reply: String? = when {
                answer != null -> answer
                else -> {
                    val fb = Store.fallback(this)
                    val last = fallbackAt[chatKey] ?: 0L
                    if (fb.isBlank()) null
                    else if (isGroup && Store.silentGroups(this)) null
                    else if (now - last < 30 * 60 * 1000L) null
                    else {
                        fallbackAt[chatKey] = now
                        fb
                    }
                }
            }
            if (reply == null) continue
            if (send(sbn, reply)) {
                recentReplies.add(Sheet.norm(reply) to System.currentTimeMillis())
                Store.log("Cevap: \"${m.text.take(40)}\" → \"${reply.take(40)}\"")
                Thread.sleep(1200)
            } else {
                Store.log("Cevap gönderilemedi (Yanıtla düğmesi yok): $title")
            }
        }
    }

    private fun send(sbn: StatusBarNotification, text: String): Boolean {
        val actions = sbn.notification.actions ?: return false
        val action = actions.firstOrNull {
            it.remoteInputs?.isNotEmpty() == true &&
                it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY
        } ?: actions.firstOrNull { it.remoteInputs?.isNotEmpty() == true } ?: return false
        val inputs = action.remoteInputs
        val intent = Intent()
        val bundle = Bundle()
        bundle.putCharSequence(inputs[0].resultKey, text)
        RemoteInput.addResultsToIntent(inputs, intent, bundle)
        return try {
            action.actionIntent.send(this, 0, intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroy() {
        exec.shutdown()
        super.onDestroy()
    }
}
