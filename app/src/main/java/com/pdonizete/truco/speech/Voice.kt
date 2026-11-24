package com.pdonizete.truco.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Voz própria do app (TextToSpeech em português do Brasil, velocidade 1,05).
 * [speak] fala um texto; [afterQueue] roda uma ação quando a fila de fala termina.
 *
 * O jogo depende de [afterQueue] para seguir em frente, então ela nunca pode ficar presa:
 * alguns motores de voz não avisam o fim da fala (por exemplo quando o TalkBack interrompe).
 * Por isso cada ação tem um tempo máximo, estimado pelo tamanho do texto na fila.
 */
class Voice(context: Context) {
    private enum class State { STARTING, READY, FAILED }

    private val main = Handler(Looper.getMainLooper())
    private val waiting = mutableListOf<() -> Unit>()
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private val counter = AtomicInteger()
    private var state = State.STARTING

    /** Momento estimado (SystemClock.uptimeMillis) em que a fila de fala termina. */
    private var expectedEnd = 0L
    private val tts = TextToSpeech(context.applicationContext) { status -> main.post { onInit(status) } }

    init {
        // Se o motor de voz não responder, o jogo não pode ficar parado esperando por ele.
        main.postDelayed({
            if (state == State.STARTING) {
                state = State.FAILED
                drainWaiting()
            }
        }, INIT_TIMEOUT_MS)
    }

    /** Desligada, a voz não fala e [afterQueue] roda a ação na hora. */
    var enabled = true
        set(value) {
            field = value
            if (!value) stop()
        }

    private fun onInit(status: Int) {
        if (state == State.READY) return
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
        drainWaiting()
    }

    private fun drainWaiting() {
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
                val now = SystemClock.uptimeMillis()
                val start = if (flush) now else maxOf(now, expectedEnd)
                expectedEnd = start + estimateMs(text)
                val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                tts.speak(text, mode, null, "f" + counter.incrementAndGet())
            }
        }
    }

    /** Roda [action] uma única vez, quando a fila de fala terminar ou quando passar o tempo máximo. */
    fun afterQueue(action: () -> Unit) {
        when {
            !enabled || state == State.FAILED -> main.post(action)
            state == State.STARTING -> waiting += { afterQueue(action) }
            else -> {
                val id = "q" + counter.incrementAndGet()
                val done = AtomicBoolean(false)
                val run: () -> Unit = { if (done.compareAndSet(false, true)) action() }
                callbacks[id] = run
                val wait = maxOf(0L, expectedEnd - SystemClock.uptimeMillis()) + GRACE_MS
                main.postDelayed({
                    callbacks.remove(id)
                    run()
                }, wait)
                if (tts.playSilentUtterance(1, TextToSpeech.QUEUE_ADD, id) != TextToSpeech.SUCCESS) finished(id)
            }
        }
    }

    fun stop() {
        if (state == State.READY) tts.stop()
        expectedEnd = 0L
        releaseCallbacks()
    }

    fun shutdown() {
        main.removeCallbacksAndMessages(null)
        callbacks.clear()
        tts.shutdown()
    }

    private companion object {
        const val INIT_TIMEOUT_MS = 6000L
        const val GRACE_MS = 1500L

        /** Estimativa folgada da duração da fala: cerca de 14 caracteres por segundo. */
        fun estimateMs(text: String): Long = 500L + text.length * 70L
    }
}
