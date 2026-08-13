package net.guitar0.metronome

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetronomeEngineHolderTest {

    private val attached = mutableListOf<FakePlaybackController>()

    private fun attach(engine: FakePlaybackController = FakePlaybackController()) = engine.also {
        MetronomeEngineHolder.attach(it)
        attached += it
    }

    // The holder is a process-wide object, so every test has to hand it back empty.
    @After
    fun tearDown() {
        attached.forEach { MetronomeEngineHolder.detach(it) }
        attached.clear()
        MetronomeEngineHolder.backgroundOptions = null
        MetronomeEngineHolder.bpm = 0.0
    }

    @Test
    fun reports_the_attached_engine_state() {
        val engine = attach()

        assertTrue(MetronomeEngineHolder.isRunning)

        engine.stop("explicit")

        assertFalse(MetronomeEngineHolder.isRunning)
    }

    @Test
    fun is_not_running_without_an_engine() {
        assertFalse(MetronomeEngineHolder.isRunning)
    }

    @Test
    fun forwards_the_stop_reason() {
        val engine = attach()

        MetronomeEngineHolder.stop(MetronomeService.STOP_REASON_NOTIFICATION)

        assertEquals(listOf(MetronomeService.STOP_REASON_NOTIFICATION), engine.stopReasons)
    }

    @Test
    fun a_null_reason_is_forwarded_so_the_engine_can_stop_without_emitting_onStop() {
        val engine = attach()

        MetronomeEngineHolder.stop(null)

        assertEquals(listOf<String?>(null), engine.stopReasons)
    }

    @Test
    fun detaching_clears_the_background_options() {
        val engine = attach()
        MetronomeEngineHolder.backgroundOptions = BackgroundOptions()

        MetronomeEngineHolder.detach(engine)

        assertNull(MetronomeEngineHolder.backgroundOptions)
        assertFalse(MetronomeEngineHolder.isRunning)
    }

    /**
     * A JS reload builds the replacement module before tearing the old one down, so
     * the stale OnDestroy must not detach the engine that already replaced it.
     */
    @Test
    fun detaching_a_replaced_engine_leaves_the_current_one_alone() {
        val old = attach()
        val current = attach()
        MetronomeEngineHolder.backgroundOptions = BackgroundOptions()

        MetronomeEngineHolder.detach(old)

        assertNotNull(MetronomeEngineHolder.backgroundOptions)
        assertTrue(MetronomeEngineHolder.isRunning)

        MetronomeEngineHolder.stop("explicit")

        assertEquals(listOf("explicit"), current.stopReasons)
        assertEquals(emptyList<String?>(), old.stopReasons)
    }
}
