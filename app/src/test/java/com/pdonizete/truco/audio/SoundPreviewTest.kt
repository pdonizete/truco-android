package com.pdonizete.truco.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Confere os sons e grava cada um como WAV em sons-previa/, na raiz do projeto. */
class SoundPreviewTest {
    private val expectedSeconds = mapOf(
        "carta" to 0.08, "distribuir" to 0.12, "embaralhar" to 1.5, "vira" to 0.16,
        "truco" to 0.35, "aplausos" to 3.0, "vez" to 0.14,
    )

    @Test fun soundsAreDeterministicSizedAndNotClipped() {
        for ((name, make) in Synth.ALL) {
            val a = make()
            assertArrayEquals("$name sai igual toda vez", a, make())
            val seconds = a.size.toDouble() / Synth.RATE
            assertTrue("$name dura $seconds s", kotlin.math.abs(seconds - expectedSeconds.getValue(name)) < 0.005)
            val peak = a.maxOf { kotlin.math.abs(it.toInt()) }
            assertTrue("$name tem pico $peak", peak in 5000..32000)
        }
    }

    @Test fun writePreviewWavs() {
        val here = File(System.getProperty("user.dir")).absoluteFile
        val root = if (here.name == "app") here.parentFile else here
        val dir = File(root, "sons-previa").apply { mkdirs() }
        for ((name, make) in Synth.ALL) {
            val file = File(dir, "$name.wav")
            file.writeBytes(Synth.wav(make()))
            assertTrue(file.length() > 44)
        }
    }
}
