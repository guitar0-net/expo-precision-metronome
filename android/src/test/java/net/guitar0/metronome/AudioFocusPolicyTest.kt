package net.guitar0.metronome

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The behaviour change in 2.0: an interruption no longer collapses into a stop.
 * These are the rules stated as a table, so a regression shows up here rather than
 * as a metronome that never comes back after a phone call.
 */
class AudioFocusPolicyTest {

    private fun outcome(
        change: Int,
        paused: Boolean = false,
        pausedByTransientLoss: Boolean = false
    ) = AudioFocusPolicy.outcome(change, paused, pausedByTransientLoss)

    @Test
    fun `a transient loss pauses rather than stopping`() {
        assertEquals(FocusOutcome.Pause, outcome(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
    }

    @Test
    fun `regaining focus after a transient loss resumes`() {
        assertEquals(
            FocusOutcome.Resume,
            outcome(
                AudioManager.AUDIOFOCUS_GAIN,
                paused = true,
                pausedByTransientLoss = true
            )
        )
    }

    /** Another app took over for good: no GAIN will follow, so there is no way back. */
    @Test
    fun `a permanent loss stops`() {
        assertEquals(FocusOutcome.Stop, outcome(AudioManager.AUDIOFOCUS_LOSS))
    }

    /**
     * Otherwise a call taken during a deliberate pause would restart the metronome in
     * the user's pocket when it ended.
     */
    @Test
    fun `a transient loss during a deliberate pause is not recorded as an interruption`() {
        assertEquals(
            FocusOutcome.Ignore,
            outcome(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, paused = true)
        )
    }

    @Test
    fun `regaining focus does not undo a pause the user asked for`() {
        assertEquals(
            FocusOutcome.Ignore,
            outcome(
                AudioManager.AUDIOFOCUS_GAIN,
                paused = true,
                pausedByTransientLoss = false
            )
        )
    }

    /** The system ducks us; going silent for a notification chime is the intent. */
    @Test
    fun `a duckable loss is left to the system`() {
        assertEquals(
            FocusOutcome.Ignore,
            outcome(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        )
    }

    @Test
    fun `an unrecognised focus change changes nothing`() {
        assertEquals(FocusOutcome.Ignore, outcome(Int.MIN_VALUE))
    }
}
