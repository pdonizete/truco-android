package com.pdonizete.truco.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Voz própria do app (TextToSpeech em português do Brasil, velocidade 1,05).
 * [speak] fala um texto; [afterQueue] roda uma ação quando a fila de fala termina.
 * Pedidos feitos antes de o motor de voz iniciar ficam guardados e rodam depois.
 */
class Voice(context: Context) {
    private enum class State { STARTING, READY, FAILED }

    private val main = Handler(Looper.getMainLooper())
    private val waiting = mutableListOf<() -> Unit>()
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private val counter = AtomicInteger()
    private var state = State.STARTING
    private val tts = TextToSpeech(context.applicationContext) { status -> main.post { onInit(status) } }

    /** Desligada, a voz não fala e [afterQueue] roda a ação na hora. */
    var enabled = true
        set(value) {
            field = value
            if (!value) stop()
        }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            state = State.FAILED
        } else {
            val r = tts.setLanguage(Locale.forLanguageTag("pt-BR"))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.forLanguageTag("pt"))
            }
            tts.setSpeechRate(1.05f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) = finished(utteranceId)
                override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
                override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finished(utteranceId)
            })
            state = State.READY
        }
        val queued = waiting.toList()
        waiting.clear()
        queued.forEach { it() }
    }

    private fun finished(id: String?) {
        id ?: return
        callbacks.remove(id)?.let { main.post(it) }
    }

    /** Roda já as ações que esperavam o fim da fala (uma fala com flush apagaria a fila). */
    private fun releaseCallbacks() {
        val list = callbacks.values.toList()
        callbacks.clear()
        list.forEach { main.post(it) }
    }

    /** [flush] = true interrompe o que estava sendo falado. */
    fun speak(text: String, flush: Boolean = false) {
        if (!enabled || text.isBlank()) return
        when (state) {
            State.FAILED -> return
            State.STARTING -> waiting += { speak(text, flush) }
            State.READY -> {
                if (flush) releaseCallbacks()
                val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                tts.speak(text, mode, null, "f" + counter.incrementAndGet())
            }
        }
    }

    /** Roda [action] quando tudo o que está na fila de fala terminar. */
    fun afterQueue(action: () -> Unit) {
        when {
            !enabled || state == State.FAILED -> main.post(action)
            state == State.STARTING -> waiting += { afterQueue(action) }
            else -> {
                val id = "q" + counter.incrementAndGet()
                callbacks[id] = action
                if (tts.playSilentUtterance(1, TextToSpeech.QUEUE_ADD, id) != TextToSpeech.SUCCESS) finished(id)
            }
        }
    }

    fun stop() {
        if (state == State.READY) tts.stop()
        releaseCallbacks()
    }

    fun shutdown() {
        callbacks.clear()
        tts.shutdown()
    }
}
