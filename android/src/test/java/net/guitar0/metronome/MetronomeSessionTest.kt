package net.guitar0.metronome

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The state machine is exercised directly: with the state reduced to one immutable
 * snapshot there is no engine to stand in for, so there are no mocks here at all.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MetronomeSessionTest {

    private val transitions = mutableListOf<Pair<MetronomeState, MetronomeState>>()

    @Before
    fun setUp() {
        MetronomeSession.reset()
        MetronomeSession.addListener { old, new -> transitions += old to new }
    }

    // The session is a process-wide object, so every test has to hand it back empty.
    @After
    fun tearDown() {
        MetronomeSession.reset()
        transitions.clear()
    }

    /** Listeners are dispatched on the main looper, which Robolectric holds paused. */
    private fun settle() = shadowOf(Looper.getMainLooper()).idle()

    private fun start(bpm: Double = 120.0, background: BackgroundOptions? = BackgroundOptions()) {
        MetronomeSession.start(bpm, background)
        settle()
        transitions.clear()
    }

    @Test
    fun starts_stopped() {
        assertEquals(Playback.Stopped, MetronomeSession.state.playback)
        assertEquals(0.0, MetronomeSession.state.bpm, 0.0)
        assertNull(MetronomeSession.state.background)
    }

    @Test
    fun start_records_the_tempo_and_the_notification_options() {
        val options = BackgroundOptions()

        MetronomeSession.start(132.0, options)

        val state = MetronomeSession.state
        assertEquals(Playback.Running, state.playback)
        assertEquals(132.0, state.bpm, 0.0)
        assertSame(options, state.background)
    }

    /** A restart must not pass through Stopped, or the notification blinks on every start(). */
    @Test
    fun restarting_is_one_transition_and_never_reports_stopped() {
        start()

        MetronomeSession.start(90.0, BackgroundOptions())
        settle()

        assertEquals(1, transitions.size)
        assertEquals(Playback.Running, transitions.single().second.playback)
    }

    @Test
    fun pause_and_resume_move_between_running_and_paused() {
        start()

        MetronomeSession.pause("explicit")
        assertEquals(Playback.Paused, MetronomeSession.state.playback)

        MetronomeSession.resume("explicit")
        assertEquals(Playback.Running, MetronomeSession.state.playback)
    }

    /**
     * The notification shade is responsive enough to double-tap by accident, and a
     * consumer counting transitions must not drift because of it.
     */
    @Test
    fun a_repeated_command_is_a_no_op_and_dispatches_nothing() {
        start()
        MetronomeSession.pause("notification")
        settle()
        transitions.clear()

        MetronomeSession.pause("notification")
        settle()

        assertTrue(transitions.isEmpty())
        assertEquals(Playback.Paused, MetronomeSession.state.playback)
    }

    /**
     * "The user taps Resume in the shade at the moment JS called stop()" is unavoidable
     * and is not an application error.
     */
    @Test
    fun pause_and_resume_are_ignored_while_stopped() {
        MetronomeSession.pause("notification")
        MetronomeSession.resume("notification")
        settle()

        assertTrue(transitions.isEmpty())
        assertEquals(Playback.Stopped, MetronomeSession.state.playback)
    }

    @Test
    fun stop_ends_playback_from_paused_as_well_as_running() {
        start()
        MetronomeSession.pause("notification")

        MetronomeSession.stop("explicit")

        assertEquals(Playback.Stopped, MetronomeSession.state.playback)
    }

    /** The options used to be cleared from two places and could disagree in between. */
    @Test
    fun stop_clears_the_notification_options_with_the_same_transition() {
        start()

        MetronomeSession.stop("explicit")
        settle()

        val state = MetronomeSession.state
        assertNull(state.background)
        assertEquals("explicit", state.reason)
    }

    @Test
    fun stopping_twice_dispatches_once() {
        start()

        MetronomeSession.stop("explicit")
        MetronomeSession.stop("notification")
        settle()

        assertEquals(1, transitions.size)
        assertEquals("explicit", transitions.single().second.reason)
    }

    @Test
    fun a_silent_stop_carries_no_reason_so_nothing_is_reported_to_js() {
        start()

        MetronomeSession.stop(null)
        settle()

        assertEquals(Playback.Stopped, transitions.single().second.playback)
        assertNull(transitions.single().second.reason)
    }

    @Test
    fun the_tempo_applies_in_every_state() {
        MetronomeSession.setBpm(60.0)
        assertEquals(60.0, MetronomeSession.state.bpm, 0.0)

        start()
        MetronomeSession.pause("explicit")
        MetronomeSession.setBpm(180.0)

        assertEquals(180.0, MetronomeSession.state.bpm, 0.0)
        assertEquals(Playback.Paused, MetronomeSession.state.playback)
    }

    /** Where the app is has nothing to do with playback, so it survives both. */
    @Test
    fun the_foreground_flag_outlives_start_and_stop() {
        MetronomeSession.setForeground(false)

        MetronomeSession.start(120.0, BackgroundOptions())
        assertFalse(MetronomeSession.state.appInForeground)

        MetronomeSession.stop("explicit")
        assertFalse(MetronomeSession.state.appInForeground)
    }

    @Test
    fun repeating_the_foreground_flag_dispatches_nothing() {
        start()

        MetronomeSession.setForeground(true)
        settle()

        assertTrue("the app was already in the foreground", transitions.isEmpty())
    }

    @Test
    fun listeners_receive_the_snapshots_either_side_of_the_transition() {
        start(bpm = 100.0)

        MetronomeSession.setBpm(101.0)
        settle()

        val (old, new) = transitions.single()
        assertEquals(100.0, old.bpm, 0.0)
        assertEquals(101.0, new.bpm, 0.0)
    }

    /** One predictable ordering for the service and for sendEvent() alike. */
    @Test
    fun listeners_are_dispatched_on_the_main_looper_not_inline() {
        MetronomeSession.start(120.0, null)

        assertTrue("must not be called before the looper runs", transitions.isEmpty())

        settle()

        assertEquals(1, transitions.size)
    }

    @Test
    fun a_removed_listener_stops_receiving_transitions() {
        val listener = MetronomeSession.Listener { _, _ -> throw AssertionError("still listening") }
        MetronomeSession.addListener(listener)
        MetronomeSession.removeListener(listener)

        MetronomeSession.start(120.0, null)
        settle()

        assertEquals(1, transitions.size)
    }
}
