package net.guitar0.metronome

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickSynthesizerTest {

    // Kotlin mirror of ClickSynthesizer.h — tested here so no JNI is required.
    private enum class SoundPreset { Click, Beep, Woodblock, Rim, Hihat, Cowbell }

    private object ClickSynthesizer {
        fun clickSeconds(preset: SoundPreset): Double = when (preset) {
            SoundPreset.Click -> 0.010
            SoundPreset.Beep -> 0.020
            SoundPreset.Woodblock -> 0.008
            SoundPreset.Rim -> 0.006
            SoundPreset.Hihat -> 0.008
            SoundPreset.Cowbell -> 0.250
        }

        fun clickDuration(sampleRate: Double, preset: SoundPreset): Int =
            (clickSeconds(preset) * sampleRate).toInt()

        fun render(
            buffer: FloatArray,
            startFrame: Int,
            clickPhase: Int,
            count: Int,
            sampleRate: Double,
            preset: SoundPreset
        ) {
            if (count <= 0) return
            when (preset) {
                SoundPreset.Click ->
                    renderSine(buffer, startFrame, clickPhase, count, sampleRate, 1000.0, 0.002)

                SoundPreset.Beep ->
                    renderSine(buffer, startFrame, clickPhase, count, sampleRate, 880.0, 0.008)

                SoundPreset.Woodblock ->
                    renderSine(buffer, startFrame, clickPhase, count, sampleRate, 400.0, 0.001)

                SoundPreset.Rim ->
                    renderDualSine(
                        buffer,
                        startFrame,
                        clickPhase,
                        count,
                        sampleRate,
                        800.0,
                        1600.0,
                        0.0012
                    )

                SoundPreset.Hihat ->
                    renderNoise(buffer, startFrame, clickPhase, count, sampleRate, 0.0015)

                SoundPreset.Cowbell ->
                    renderDualSine(
                        buffer,
                        startFrame,
                        clickPhase,
                        count,
                        sampleRate,
                        562.0,
                        845.0,
                        0.05
                    )
            }
        }

        private fun renderSine(
            buffer: FloatArray,
            startFrame: Int,
            clickPhase: Int,
            count: Int,
            sampleRate: Double,
            freq: Double,
            decayTau: Double
        ) {
            val twoPiF = 2.0 * PI * freq
            for (i in 0 until count) {
                val t = (clickPhase + i).toDouble() / sampleRate
                buffer[startFrame + i] += (sin(twoPiF * t) * exp(-t / decayTau)).toFloat()
            }
        }

        private fun renderDualSine(
            buffer: FloatArray,
            startFrame: Int,
            clickPhase: Int,
            count: Int,
            sampleRate: Double,
            freq1: Double,
            freq2: Double,
            decayTau: Double
        ) {
            val twoPiF1 = 2.0 * PI * freq1
            val twoPiF2 = 2.0 * PI * freq2
            for (i in 0 until count) {
                val t = (clickPhase + i).toDouble() / sampleRate
                val amp = exp(-t / decayTau).toFloat()
                buffer[startFrame + i] +=
                    ((sin(twoPiF1 * t) + sin(twoPiF2 * t)) * 0.5 * amp).toFloat()
            }
        }

        private fun renderNoise(
            buffer: FloatArray,
            startFrame: Int,
            clickPhase: Int,
            count: Int,
            sampleRate: Double,
            decayTau: Double
        ) {
            for (i in 0 until count) {
                val t = (clickPhase + i).toDouble() / sampleRate
                val amp = exp(-t / decayTau).toFloat()
                var x = (clickPhase + i).toUInt()
                x = x * 1664525u + 1013904223u
                x = (x xor (x shr 16)) * 0x45d9f3b7u
                x = x xor (x shr 16)
                val noise = (x.toFloat() / (1u shl 31).toFloat()) * 2.0f - 1.0f
                buffer[startFrame + i] += noise * amp
            }
        }
    }

    private val sampleRate = 44100.0

    @Test
    fun `samples before start offset remain zero`() {
        val startFrame = 100
        val count = ClickSynthesizer.clickDuration(sampleRate, SoundPreset.Click)
        val buffer = FloatArray(startFrame + count)

        ClickSynthesizer.render(buffer, startFrame, 0, count, sampleRate, SoundPreset.Click)

        for (i in 0 until startFrame) {
            assertEquals("Sample $i before offset should be zero", 0.0f, buffer[i])
        }
    }

    @Test
    fun `samples at and after start offset are non-zero`() {
        val startFrame = 50
        val count = ClickSynthesizer.clickDuration(sampleRate, SoundPreset.Click)
        val buffer = FloatArray(startFrame + count)

        ClickSynthesizer.render(buffer, startFrame, 0, count, sampleRate, SoundPreset.Click)

        val anyNonZero = (startFrame until startFrame + count).any { buffer[it] != 0.0f }
        assertTrue("At least some rendered samples must be non-zero", anyNonZero)
    }

    @Test
    fun `click duration is positive for all presets`() {
        for (preset in SoundPreset.entries) {
            val dur = ClickSynthesizer.clickDuration(sampleRate, preset)
            assertTrue("$preset clickDuration must be positive, got $dur", dur > 0)
        }
    }

    @Test
    fun `cowbell is longer than click`() {
        val cowbell = ClickSynthesizer.clickDuration(sampleRate, SoundPreset.Cowbell)
        val click = ClickSynthesizer.clickDuration(sampleRate, SoundPreset.Click)
        assertTrue("cowbell ($cowbell) should be longer than click ($click)", cowbell > click)
    }

    @Test
    fun `each preset renders non-zero samples`() {
        for (preset in SoundPreset.entries) {
            val count = ClickSynthesizer.clickDuration(sampleRate, preset)
            val buffer = FloatArray(count)
            // Use clickPhase=1 so even sin-based presets (where sin(0)=0) produce non-zero output.
            ClickSynthesizer.render(buffer, 0, 1, count, sampleRate, preset)
            val anyNonZero = buffer.any { it != 0.0f }
            assertTrue("$preset render produced all-zero output", anyNonZero)
        }
    }

    @Test
    fun `render with zero count writes nothing`() {
        val buffer = FloatArray(256) { 1.0f }
        ClickSynthesizer.render(buffer, 0, 0, 0, sampleRate, SoundPreset.Click)
        buffer.forEach { assertEquals("Buffer should be unchanged", 1.0f, it) }
    }

    @Test
    fun `render accumulates onto existing buffer contents`() {
        val count = ClickSynthesizer.clickDuration(sampleRate, SoundPreset.Click)
        val buffer = FloatArray(count) { 0.5f }

        ClickSynthesizer.render(buffer, 0, 0, count, sampleRate, SoundPreset.Click)

        val anyChanged = buffer.any { it != 0.5f }
        assertTrue("Render should accumulate onto existing buffer", anyChanged)
    }
}
