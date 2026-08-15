package net.guitar0.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatSchedulerTest {

    // Kotlin mirror of BeatScheduler.h — tested here so no JNI is required.
    private class BeatScheduler {
        var beatCounter = 0
        private var nextBeatSample = 0L

        fun reset() {
            beatCounter = 0
            nextBeatSample = 0L
        }

        data class BeatResult(val offset: Int, val beatNumber: Int)

        fun nextBeat(
            frameCount: Int,
            currentSample: Long,
            bpm: Double,
            sampleRate: Double
        ): BeatResult {
            if (bpm <= 0 || sampleRate <= 0) return BeatResult(-1, -1)
            val interval = (sampleRate * 60.0 / bpm).toLong()
            if (interval <= 0) return BeatResult(-1, -1)
            if (nextBeatSample < currentSample) nextBeatSample = currentSample
            if (nextBeatSample >= currentSample + frameCount) return BeatResult(-1, -1)
            val offset = (nextBeatSample - currentSample).toInt()
            val beat = beatCounter++
            nextBeatSample += interval
            return BeatResult(offset, beat)
        }
    }

    private val sampleRate = 44100.0

    @Test
    fun `beat interval at 120 BPM 44100 Hz matches floor computation`() {
        val scheduler = BeatScheduler()
        val expectedInterval = (sampleRate * 60.0 / 120.0).toLong() // 22050

        val beatSamples = mutableListOf<Long>()
        var currentSample = 0L
        val bufferSize = 256

        while (beatSamples.size < 2) {
            val result = scheduler.nextBeat(bufferSize, currentSample, 120.0, sampleRate)
            if (result.offset >= 0) beatSamples.add(currentSample + result.offset)
            currentSample += bufferSize
        }

        val actualInterval = beatSamples[1] - beatSamples[0]
        assertTrue(
            "Expected interval $expectedInterval ± 1, got $actualInterval",
            Math.abs(actualInterval - expectedInterval) <= 1
        )
    }

    @Test
    fun `beat interval at representative BPM values matches floor computation`() {
        for (bpm in listOf(60.0, 90.0, 120.0, 180.0, 240.0, 300.0)) {
            val scheduler = BeatScheduler()
            val expected = (sampleRate * 60.0 / bpm).toLong()

            val beatSamples = mutableListOf<Long>()
            var currentSample = 0L
            while (beatSamples.size < 2) {
                val result = scheduler.nextBeat(256, currentSample, bpm, sampleRate)
                if (result.offset >= 0) beatSamples.add(currentSample + result.offset)
                currentSample += 256
            }

            val actual = beatSamples[1] - beatSamples[0]
            assertTrue(
                "BPM $bpm: expected interval $expected ± 1, got $actual",
                Math.abs(actual - expected) <= 1
            )
        }
    }

    @Test
    fun `bpm change mid-stream produces no duplicate or skipped beat`() {
        val scheduler = BeatScheduler()
        val bufferSize = 512
        val allBeats = mutableListOf<Pair<Long, Int>>() // (samplePosition, beatNumber)
        var currentSample = 0L
        var bpm = 120.0

        for (i in 0 until 200) {
            if (i == 100) bpm = 180.0
            val result = scheduler.nextBeat(bufferSize, currentSample, bpm, sampleRate)
            if (result.offset >= 0) {
                allBeats.add(Pair(currentSample + result.offset, result.beatNumber))
            }
            currentSample += bufferSize
        }

        assertTrue("Should have observed beats", allBeats.isNotEmpty())

        // Beat numbers must be sequential: 0, 1, 2, ...
        allBeats.forEachIndexed { index, (_, beatNumber) ->
            assertEquals("Beat at index $index should have number $index", index, beatNumber)
        }

        // Sample positions must be strictly increasing (no rewind after BPM change)
        for (i in 1 until allBeats.size) {
            assertTrue(
                "Beat $i sample should come after beat ${i - 1}",
                allBeats[i].first > allBeats[i - 1].first
            )
        }
    }

    @Test
    fun `beat counter is monotonically increasing`() {
        val scheduler = BeatScheduler()
        var lastBeat = -1
        var currentSample = 0L

        repeat(500) {
            val result = scheduler.nextBeat(256, currentSample, 120.0, sampleRate)
            if (result.offset >= 0) {
                assertEquals("Beat counter should increment by 1", lastBeat + 1, result.beatNumber)
                lastBeat = result.beatNumber
            }
            currentSample += 256
        }

        assertTrue("Should have observed multiple beats", lastBeat > 0)
    }

    /**
     * Pause silences the callback without closing the stream, so the sample clock runs
     * on through it. Resume calls reset(), which is what makes playback re-enter on the
     * downbeat: a musician pauses by ear at an arbitrary moment and comes back on "one".
     */
    @Test
    fun `resuming after a silent stretch fires the downbeat immediately`() {
        val scheduler = BeatScheduler()
        val frames = 256
        var currentSample = 0L

        // Play until a few beats have gone by.
        var beatsBefore = 0
        while (beatsBefore < 3) {
            if (scheduler.nextBeat(frames, currentSample, 120.0, sampleRate).offset >= 0) {
                beatsBefore++
            }
            currentSample += frames
        }

        // Paused: the callback writes silence and never asks the scheduler anything,
        // but the sample clock keeps advancing.
        currentSample += frames * 400L

        scheduler.reset()
        val resumed = scheduler.nextBeat(frames, currentSample, 120.0, sampleRate)

        assertEquals("Resume must click on the first buffer, not part-way in", 0, resumed.offset)
        assertEquals("The bar starts over on resume", 0, resumed.beatNumber)
    }

    /** The interval after a resume must be the plain tempo, not a catch-up burst. */
    @Test
    fun `resuming does not replay the beats missed while paused`() {
        val scheduler = BeatScheduler()
        val frames = 256
        var currentSample = frames * 1000L

        scheduler.reset()
        assertEquals(0, scheduler.nextBeat(frames, currentSample, 120.0, sampleRate).offset)
        val firstBeatSample = currentSample
        currentSample += frames

        var secondBeatSample = -1L
        while (secondBeatSample < 0) {
            val result = scheduler.nextBeat(frames, currentSample, 120.0, sampleRate)
            if (result.offset >= 0) secondBeatSample = currentSample + result.offset
            currentSample += frames
        }

        val interval = (sampleRate * 60.0 / 120.0).toLong()
        assertEquals(
            "Second beat should land one plain interval later",
            interval,
            secondBeatSample - firstBeatSample
        )
    }
}
