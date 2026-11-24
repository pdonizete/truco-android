package com.pdonizete.truco.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Síntese dos sons de baralho em PCM 16 bits mono a 22050 Hz.
 * Sem dependência do Android: o app toca com AudioTrack e o teste grava WAV.
 * Cada som usa uma semente fixa, então sai igual toda vez.
 * Papel soa como ruído filtrado com envelope curto, quase sem tom.
 */
object Synth {
    const val RATE = 22050

    /** Nome do arquivo de prévia e função que gera o som. */
    val ALL: Map<String, () -> ShortArray> = linkedMapOf(
        "carta" to ::card,
        "distribuir" to ::deal,
        "embaralhar" to ::shuffle,
        "vira" to ::vira,
        "truco" to ::truco,
        "aplausos" to ::applause,
        "vez" to ::turn,
    )

    fun card(): ShortArray = pcm(rawCard(Random(11)), 0.70)
    fun deal(): ShortArray = pcm(rawDeal(Random(12)), 0.45)
    fun shuffle(): ShortArray = pcm(rawShuffle(Random(13)), 0.60)
    fun vira(): ShortArray = pcm(rawVira(Random(14)), 0.60)
    fun truco(): ShortArray = pcm(rawTruco(Random(15)), 0.95)
    fun applause(): ShortArray = pcm(rawApplause(Random(16)), 0.70)
    fun turn(): ShortArray = pcm(rawTurn(), 0.35)

    private fun len(seconds: Double) = (RATE * seconds).toInt()
    private fun noise(r: Random) = r.nextDouble() * 2 - 1

    /** Carta batendo na mesa: estalo seco de papel (passa-banda 2 a 5 kHz) e um baque grave bem fraco. */
    private fun rawCard(r: Random): FloatArray {
        val out = FloatArray(len(0.08))
        val bp = Biquad().apply { bandPass(3200.0, 1.0) }
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            val snap = bp.run(noise(r)) * min(1.0, t / 0.0008) * exp(-t / 0.011)
            val thump = sin(2 * PI * 110 * t) * min(1.0, t / 0.002) * exp(-t / 0.018) * 0.06
            out[i] = (snap + thump).toFloat()
        }
        return out
    }

    /** Carta deslizando ao ser distribuída: ruído com o filtro subindo de 900 Hz a 4,5 kHz. */
    private fun rawDeal(r: Random, seconds: Double = 0.12): FloatArray {
        val out = FloatArray(len(seconds))
        val bp = Biquad()
        for (i in out.indices) {
            val x = i.toDouble() / out.size
            bp.bandPass(900 + 3600 * x.pow(1.2), 0.9)
            val env = sin(PI * x).pow(0.7) * (1 - 0.5 * x)
            out[i] = (bp.run(noise(r)) * env).toFloat()
        }
        return out
    }

    /** Um "flip" de papel bem curto, usado no embaralhar e na vira. */
    private fun rawFlip(r: Random, center: Double): FloatArray {
        val out = FloatArray(len(0.03))
        val bp = Biquad().apply { bandPass(center, 1.4) }
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            out[i] = (bp.run(noise(r)) * min(1.0, t / 0.001) * exp(-t / 0.006)).toFloat()
        }
        return out
    }

    /** Embaralhar (~1,5 s): um riffle de dezenas de flips, um chiado de fundo e as metades se juntando. */
    private fun rawShuffle(r: Random): FloatArray {
        val mix = FloatArray(len(1.5))
        val flips = 52
        for (k in 0 until flips) {
            val at = 0.10 + 1.10 * (k.toDouble() / flips).pow(0.9) + (r.nextDouble() - 0.5) * 0.008
            add(mix, len(at), rawFlip(r, 2500 + r.nextDouble() * 2000), 0.5 + r.nextDouble() * 0.5)
        }
        val lp = Biquad().apply { lowPass(1500.0, 0.7) }
        val start = len(0.10)
        val end = len(1.22)
        for (i in start until end) {
            val x = (i - start).toDouble() / (end - start)
            mix[i] += (lp.run(noise(r)) * sin(PI * x) * 0.15).toFloat()
        }
        add(mix, len(1.28), rawDeal(r, 0.18), 0.6)
        return mix
    }

    /** Virar a vira: um flip e um estalo leve logo depois. */
    private fun rawVira(r: Random): FloatArray {
        val mix = FloatArray(len(0.16))
        add(mix, 0, rawFlip(r, 3000.0), 0.8)
        add(mix, len(0.07), rawCard(r), 0.6)
        return mix
    }

    /** Batida forte na mesa: baque grave caindo de 140 para 60 Hz, ruído abafado e o estalo da mão. */
    private fun rawTruco(r: Random): FloatArray {
        val out = FloatArray(len(0.35))
        val lp = Biquad().apply { lowPass(900.0, 0.7) }
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / RATE
            phase += 2 * PI * (60 + 80 * exp(-t / 0.04)) / RATE
            val attack = min(1.0, t / 0.001)
            val thump = sin(phase) * attack * exp(-t / 0.06)
            val body = lp.run(noise(r)) * attack * exp(-t / 0.02) * 0.8
            out[i] = (thump + body).toFloat()
        }
        add(out, 0, rawCard(r), 0.4)
        return out
    }

    /** Aplausos (~3 s): centenas de palmas curtas de ruído filtrado, sobrepostas. */
    private fun rawApplause(r: Random): FloatArray {
        val total = 3.0
        val mix = FloatArray(len(total))
        val clap = len(0.025)
        repeat(450) {
            val bp = Biquad().apply { bandPass(1000 + r.nextDouble() * 1500, 1.2) }
            val start = r.nextInt(mix.size - clap)
            val gain = 0.4 + r.nextDouble() * 0.6
            for (j in 0 until clap) {
                val t = j.toDouble() / RATE
                mix[start + j] += (bp.run(noise(r)) * exp(-t / 0.005) * gain).toFloat()
            }
        }
        for (i in mix.indices) {
            val t = i.toDouble() / RATE
            mix[i] *= (min(1.0, t / 0.25) * min(1.0, (total - t) / 1.2)).coerceAtLeast(0.0).toFloat()
        }
        return mix
    }

    /** Bipe curto e suave de 784 Hz com bordas arredondadas. */
    private fun rawTurn(): FloatArray {
        val out = FloatArray(len(0.14))
        val rise = len(0.01)
        val fall = len(0.04)
        for (i in out.indices) {
            val env = when {
                i < rise -> 0.5 - 0.5 * cos(PI * i / rise)
                i > out.size - fall -> 0.5 - 0.5 * cos(PI * (out.size - i) / fall)
                else -> 1.0
            }
            out[i] = (sin(2 * PI * 784 * i / RATE) * env).toFloat()
        }
        return out
    }

    private fun add(mix: FloatArray, at: Int, src: FloatArray, gain: Double) {
        for (j in src.indices) {
            val k = at + j
            if (k in mix.indices) mix[k] += (src[j] * gain).toFloat()
        }
    }

    /** Ajusta o pico para [level] (0 a 1) e converte para 16 bits. */
    private fun pcm(mix: FloatArray, level: Double): ShortArray {
        val peak = mix.maxOf { abs(it) }.toDouble().coerceAtLeast(1e-9)
        val scale = level * Short.MAX_VALUE / peak
        return ShortArray(mix.size) { (mix[it] * scale).toInt().coerceIn(-32767, 32767).toShort() }
    }

    /** Arquivo WAV (PCM 16 bits mono) com os dados de [pcm]. */
    fun wav(pcm: ShortArray): ByteArray {
        val data = pcm.size * 2
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        b.putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        pcm.forEach { b.putShort(it) }
        return b.array()
    }

    /** Filtro biquad de segunda ordem (fórmulas do RBJ Audio EQ Cookbook). */
    private class Biquad {
        private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0
        private var a1 = 0.0; private var a2 = 0.0
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

        fun bandPass(f: Double, q: Double) {
            val w = 2 * PI * f / RATE
            val alpha = sin(w) / (2 * q)
            val a0 = 1 + alpha
            b0 = alpha / a0; b1 = 0.0; b2 = -alpha / a0
            a1 = -2 * cos(w) / a0; a2 = (1 - alpha) / a0
        }

        fun lowPass(f: Double, q: Double) {
            val w = 2 * PI * f / RATE
            val alpha = sin(w) / (2 * q)
            val a0 = 1 + alpha
            val c = cos(w)
            b0 = (1 - c) / 2 / a0; b1 = (1 - c) / a0; b2 = b0
            a1 = -2 * c / a0; a2 = (1 - alpha) / a0
        }

        fun run(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }
}
