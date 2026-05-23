package net.guitar0.metronome

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetronomeStreamTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun start_then_stop_closes_the_stream() {
        val beatLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        val beatsAfterStop = AtomicInteger(0)
        var stopped = false

        val engine = MetronomeEngine(context) { eventName, _ ->
            when (eventName) {
                "onBeat" -> {
                    if (!stopped) {
                        beatLatch.countDown()
                    } else {
                        beatsAfterStop.incrementAndGet()
                    }
                }

                "onStop" -> stopLatch.countDown()
            }
        }

        try {
            engine.start(120.0)

            assertTrue(
                "Engine should emit at least one beat within 2 s",
                beatLatch.await(2, TimeUnit.SECONDS)
            )

            stopped = true
            engine.stop("explicit")

            assertTrue(
                "onStop event should fire within 1 s",
                stopLatch.await(1, TimeUnit.SECONDS)
            )

            // Wait two full beat intervals at 120 BPM (≈ 1 s) to confirm no more beats arrive.
            Thread.sleep(1_000)

            assertEquals("No onBeat events should arrive after stop()", 0, beatsAfterStop.get())
        } finally {
            engine.destroy()
        }
    }

    @Test
    fun stop_is_idempotent() {
        val engine = MetronomeEngine(context) { _, _ -> }

        try {
            engine.start(120.0)
            Thread.sleep(100)

            // Second stop() must not throw or crash.
            engine.stop("explicit")
            engine.stop("explicit")
            engine.stop(null)
        } finally {
            engine.destroy()
        }
    }

    @Test
    fun stop_without_start_is_safe() {
        val engine = MetronomeEngine(context) { _, _ -> }
        try {
            engine.stop("explicit")
            engine.stop(null)
        } finally {
            engine.destroy()
        }
    }
}
