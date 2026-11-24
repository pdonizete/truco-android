package com.pdonizete.truco.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Efeitos sonoros do jogo. Os sons vêm de [Synth], são gerados uma vez em segundo plano
 * e tocados com AudioTrack estático. Não há arquivos de áudio no APK.
 */
class SoundFx {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sounds = java.util.concurrent.ConcurrentHashMap<String, ShortArray>()
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val format = AudioFormat.Builder()
        .setSampleRate(Synth.RATE)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build()

    /** Cala os efeitos. A voz do app não é afetada. */
    @Volatile var muted = false

    init {
        scope.launch { Synth.ALL.forEach { (name, make) -> sounds[name] = make() } }
    }

    fun playCard() = play("carta")
    fun playDeal() = play("distribuir")
    fun playShuffle() = play("embaralhar")
    fun playVira() = play("vira")
    fun playTruco() = play("truco")
    fun playApplause() = play("aplausos")
    fun playTurn() = play("vez")

    private fun play(name: String) {
        if (muted) return
        val pcm = sounds[name] ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.setNotificationMarkerPosition(pcm.size - 1)
                track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack) = t.release()
                    override fun onPeriodicNotification(t: AudioTrack) {}
                })
                track.play()
            }
        }
    }

    fun release() = scope.cancel()
}
