package net.guitar0.metronome

import android.media.AudioManager

/** What a change in audio focus should do to playback. */
internal enum class FocusOutcome {
    Pause,
    Resume,
    Stop,

    /** Leave playback exactly as it is. */
    Ignore
}

/**
 * The audio-focus rules, kept as data so they can be asserted directly — the engine
 * that applies them owns an Oboe stream and an `AudioManager`, neither of which
 * exists in a unit test.
 *
 * The distinction that matters is transient versus permanent. A transient loss is a
 * phone call: the metronome the user was practising with is still what they are
 * doing, so it pauses and comes back. A permanent loss is another app taking over
 * for good; no `GAIN` will follow, so there is nothing to come back from.
 */
internal object AudioFocusPolicy {

    /**
     * @param focusChange the value handed to `OnAudioFocusChangeListener`.
     * @param paused whether playback is already silent.
     * @param pausedByTransientLoss whether the pause in effect was caused by a
     *   transient loss, as opposed to being asked for by the user.
     */
    fun outcome(focusChange: Int, paused: Boolean, pausedByTransientLoss: Boolean): FocusOutcome =
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> FocusOutcome.Stop

            // A pause the user asked for must not be turned into an interruption, or
            // the later GAIN would restart the metronome in their pocket.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ->
                if (paused) FocusOutcome.Ignore else FocusOutcome.Pause

            AudioManager.AUDIOFOCUS_GAIN ->
                if (pausedByTransientLoss) FocusOutcome.Resume else FocusOutcome.Ignore

            // LOSS_TRANSIENT_CAN_DUCK included: the system lowers the volume for us,
            // and a metronome that goes quiet for a notification chime is intended.
            else -> FocusOutcome.Ignore
        }
}
