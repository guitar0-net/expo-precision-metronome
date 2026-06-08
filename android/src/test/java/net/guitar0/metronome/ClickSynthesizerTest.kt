package net.guitar0.metronome

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickSynthesizerTest {

    // Kotlin mirror of ClickSynthesizer.h — tested here so no JNI is required.
    private enum class SoundPreset { Click, Beep, Woodblock, Rim, Hihat, Cowbell }

    private data class AccentParams(val volume: Float, val freqMult: Double, val decayMult: Double)

    private object ClickSynthesizer {
        private data class RenderCtx(
            val buffer: FloatArray,
            val startFrame: Int,
            val clickPhase: Int,
            val count: Int,
            val sampleRate: Double,
            val accent: AccentParams
        )

        fun accentParams(accent: String): AccentParams = when (accent) {
            "strong" -> AccentParams(1.3f, 1.4, 0.6)
            "muted" -> AccentParams(0.12f, 1.0, 1.0)
            else -> AccentParams(1.0f, 1.0, 1.0)
        }

        fun accentParams(accent: String, preset: SoundPreset): AccentParams {
            if (preset != SoundPreset.Hihat) return accentParams(accent)
            return when (accent) {
                "strong" -> AccentParams(1.4f, 1.0, 3.5)
                "muted" -> AccentParams(0.12f, 1.0, 1.0)
                else -> AccentParams(1.0f, 1.0, 1.0)
            }
        }

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

        fun clickDuration(sampleRate: Double, preset: SoundPreset, accent: String): Int {
            val seconds = if (preset == SoundPreset.Hihat &&
                accent == "strong"
            ) {
                0.030
            } else {
                clickSeconds(preset)
            }
            return (seconds * sampleRate).toInt()
        }

        fun render(
            buffer: FloatArray,
            startFrame: Int,
            clickPhase: Int,
            count: Int,
            sampleRate: Double,
            preset: SoundPreset,
            accent: AccentParams = AccentParams(1.0f, 1.0, 1.0)
        ) {
            if (count <= 0) return
            val ctx = RenderCtx(buffer, startFrame, clickPhase, count, sampleRate, accent)
            when (preset) {
                SoundPreset.Click -> renderSine(ctx, 1000.0, 0.002)
                SoundPreset.Beep -> renderSine(ctx, 880.0, 0.008)
                SoundPreset.Woodblock -> renderSine(ctx, 400.0, 0.001)
                SoundPreset.Rim -> renderDualSine(ctx, 800.0, 1600.0, 0.0012)
                SoundPreset.Hihat -> renderNoise(ctx, 0.0015)
                SoundPreset.Cowbell -> renderDualSine(ctx, 562.0, 845.0, 0.05)
            }
        }

        private fun renderSine(ctx: RenderCtx, freq: Double, decayTau: Double) {
            val twoPiF = 2.0 * PI * (freq * ctx.accent.freqMult)
            val tau = decayTau * ctx.accent.decayMult
            for (i in 0 until ctx.count) {
                val t = (ctx.clickPhase + i).toDouble() / ctx.sampleRate
                ctx.buffer[ctx.startFrame + i] +=
                    (sin(twoPiF * t) * exp(-t / tau) * ctx.accent.volume).toFloat()
            }
        }

        private fun renderDualSine(ctx: RenderCtx, freq1: Double, freq2: Double, decayTau: Double) {
            val twoPiF1 = 2.0 * PI * (freq1 * ctx.accent.freqMult)
            val twoPiF2 = 2.0 * PI * (freq2 * ctx.accent.freqMult)
            val tau = decayTau * ctx.accent.decayMult
            for (i in 0 until ctx.count) {
                val t = (ctx.clickPhase + i).toDouble() / ctx.sampleRate
                val amp = exp(-t / tau).toFloat()
                val wave = (sin(twoPiF1 * t) + sin(twoPiF2 * t)).toFloat() * 0.5f
                ctx.buffer[ctx.startFrame + i] += wave * amp * ctx.accent.volume
            }
        }

        private fun renderNoise(ctx: RenderCtx, decayTau: Double) {
            val tau = decayTau * ctx.accent.decayMult
            for (i in 0 until ctx.count) {
                val t = (ctx.clickPhase + i).toDouble() / ctx.sampleRate
                val amp = exp(-t / tau).toFloat() * ctx.accent.volume
                var x = (ctx.clickPhase + i).toUInt()
                x = x * 1664525u + 1013904223u
                x = (x xor (x shr 16)) * 0x45d9f3b7u
                x = x xor (x shr 16)
                val noise = ((x shr 1).toFloat() / (1u shl 31).toFloat()) * 2.0f - 1.0f
                ctx.buffer[ctx.startFrame + i] += noise * amp
            }
        }
    }

    private val sampleRate = 44100.0

    // MARK: - Existing synthesis tests

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

    // MARK: - Accent tests

    @Test
    fun `normal accent params are identity`() {
        val p = ClickSynthesizer.accentParams("normal")
        assertEquals(1.0f, p.volume)
        assertEquals(1.0, p.freqMult, 0.0)
        assertEquals(1.0, p.decayMult, 0.0)
    }

    @Test
    fun `strong accent is louder than normal`() {
        val count = 20
        val normalBuf = FloatArray(count)
        val strongBuf = FloatArray(count)

        ClickSynthesizer.render(
            normalBuf,
            0,
            1,
            count,
            sampleRate,
            SoundPreset.Click,
            ClickSynthesizer.accentParams("normal")
        )
        ClickSynthesizer.render(
            strongBuf,
            0,
            1,
            count,
            sampleRate,
            SoundPreset.Click,
            ClickSynthesizer.accentParams("strong")
        )

        assertTrue(
            "strong RMS (${rms(strongBuf)}) should exceed normal RMS (${rms(normalBuf)})",
            rms(strongBuf) > rms(normalBuf)
        )
    }

    @Test
    fun `muted accent is quieter than normal and non-silent`() {
        val count = 20
        val normalBuf = FloatArray(count)
        val mutedBuf = FloatArray(count)

        ClickSynthesizer.render(
            normalBuf,
            0,
            1,
            count,
            sampleRate,
            SoundPreset.Click,
            ClickSynthesizer.accentParams("normal")
        )
        ClickSynthesizer.render(
            mutedBuf,
            0,
            1,
            count,
            sampleRate,
            SoundPreset.Click,
            ClickSynthesizer.accentParams("muted")
        )

        assertTrue(
            "muted RMS (${rms(mutedBuf)}) should be below normal RMS (${rms(normalBuf)})",
            rms(mutedBuf) < rms(normalBuf)
        )
        assertTrue("muted accent should not be completely silent", rms(mutedBuf) > 0f)
    }

    @Test
    fun `hihat strong is louder and longer than hihat normal`() {
        val sr = sampleRate
        val normalDur = ClickSynthesizer.clickDuration(sr, SoundPreset.Hihat, "normal")
        val strongDur = ClickSynthesizer.clickDuration(sr, SoundPreset.Hihat, "strong")
        assertTrue(
            "hihat strong ($strongDur) should be longer than normal ($normalDur)",
            strongDur > normalDur
        )

        val normalBuf = FloatArray(normalDur)
        val strongBuf = FloatArray(normalDur)
        ClickSynthesizer.render(
            normalBuf,
            0,
            1,
            normalDur,
            sr,
            SoundPreset.Hihat,
            ClickSynthesizer.accentParams("normal", SoundPreset.Hihat)
        )
        ClickSynthesizer.render(
            strongBuf,
            0,
            1,
            normalDur,
            sr,
            SoundPreset.Hihat,
            ClickSynthesizer.accentParams("strong", SoundPreset.Hihat)
        )

        assertTrue(
            "hihat strong RMS (${rms(
                strongBuf
            )}) should exceed hihat normal RMS (${rms(normalBuf)})",
            rms(strongBuf) > rms(normalBuf)
        )
    }

    private fun rms(buf: FloatArray): Float {
        val sum = buf.fold(0.0) { acc, v -> acc + v.toDouble() * v }
        return sqrt(sum / buf.size).toFloat()
    }
}
