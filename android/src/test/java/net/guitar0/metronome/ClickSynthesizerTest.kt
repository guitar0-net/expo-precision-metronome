package net.guitar0.metronome

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickSynthesizerTest {

    // Kotlin mirror of ClickSynthesizer.h — tested here so no JNI is required.
    private object ClickSynthesizer {
        private const val FREQUENCY = 1000.0
        private const val CLICK_SECONDS = 0.010
        private const val DECAY_TAU = CLICK_SECONDS / 5.0

        fun clickDuration(sampleRate: Double): Int = (CLICK_SECONDS * sampleRate).toInt()

        fun render(
            buffer: FloatArray,
            startFrame: Int,
            clickPhase: Int,
            count: Int,
            sampleRate: Double
        ) {
            if (count <= 0) return
            val twoPiF = 2.0 * PI * FREQUENCY
            for (i in 0 until count) {
                val t = (clickPhase + i).toDouble() / sampleRate
                buffer[startFrame + i] += (sin(twoPiF * t) * exp(-t / DECAY_TAU)).toFloat()
            }
        }
    }

    private val sampleRate = 44100.0

    @Test
    fun `samples before start offset remain zero`() {
        val startFrame = 100
        val count = ClickSynthesizer.clickDuration(sampleRate)
        val buffer = FloatArray(startFrame + count)

        ClickSynthesizer.render(buffer, startFrame, 0, count, sampleRate)

        for (i in 0 until startFrame) {
            assertEquals("Sample $i before offset should be zero", 0.0f, buffer[i])
        }
    }

    @Test
    fun `samples at and after start offset are non-zero`() {
        val startFrame = 50
        val count = ClickSynthesizer.clickDuration(sampleRate)
        val buffer = FloatArray(startFrame + count)

        ClickSynthesizer.render(buffer, startFrame, 0, count, sampleRate)

        val anyNonZero = (startFrame until startFrame + count).any { buffer[it] != 0.0f }
        assertTrue("At least some rendered samples must be non-zero", anyNonZero)
    }

    @Test
    fun `click duration does not exceed 10ms at 44100 Hz`() {
        val maxSamples = (0.010 * sampleRate).toInt() // 441
        val actual = ClickSynthesizer.clickDuration(sampleRate)
        assertTrue(
            "Click duration $actual exceeds 10 ms ($maxSamples samples) at $sampleRate Hz",
            actual <= maxSamples
        )
    }

    @Test
    fun `click duration does not exceed 10ms at 48000 Hz`() {
        val rate = 48000.0
        val maxSamples = (0.010 * rate).toInt() // 480
        val actual = ClickSynthesizer.clickDuration(rate)
        assertTrue(
            "Click duration $actual exceeds 10 ms ($maxSamples samples) at $rate Hz",
            actual <= maxSamples
        )
    }

    @Test
    fun `render with zero count writes nothing`() {
        val buffer = FloatArray(256) { 1.0f }
        ClickSynthesizer.render(buffer, 0, 0, 0, sampleRate)
        buffer.forEach { assertEquals("Buffer should be unchanged", 1.0f, it) }
    }

    @Test
    fun `render accumulates onto existing buffer contents`() {
        val startFrame = 0
        val count = ClickSynthesizer.clickDuration(sampleRate)
        val buffer = FloatArray(count) { 0.5f }

        ClickSynthesizer.render(buffer, startFrame, 0, count, sampleRate)

        // The render adds to existing content, so at least one sample should differ from 0.5
        val anyChanged = buffer.any { it != 0.5f }
        assertTrue("Render should accumulate onto existing buffer", anyChanged)
    }
}
