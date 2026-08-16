package net.guitar0.metronome

import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Playback lifecycle. `Paused` keeps the audio engine alive and only silences the
 * scheduler; `Stopped` is the one state in which nothing is allocated.
 */
internal enum class Playback(
    /** The wire value of `PlaybackState` in TypeScript; the two must not drift apart. */
    val jsName: String
) {
    Running("running"),
    Paused("paused"),
    Stopped("stopped")
}

/**
 * Immutable snapshot of everything the module, the service and the notification have
 * to agree on. Handed to listeners as an argument so that a listener cannot re-read —
 * and therefore cannot observe — a half-applied update.
 */
internal data class MetronomeState(
    val playback: Playback = Playback.Stopped,
    val bpm: Double = 0.0,
    /** Non-null only while background playback is active — the service draws from it. */
    val background: BackgroundOptions? = null,
    /**
     * Whether the app's own UI is on screen. The notification omits the tempo while it
     * is, because a number the user can also read from the app is a number that can be
     * seen to be stale. Defaults to `true`: playback can only be started from the
     * foreground, so that is the state the first snapshot describes.
     */
    val appInForeground: Boolean = true,
    /**
     * Why [playback] last changed, or `null` when JS must not hear about it: a restart,
     * or a teardown with no JS context left to hear it. Only meaningful to a listener
     * that has just been handed a change of [playback].
     */
    val reason: String? = null
)

/**
 * Process-wide, observable playback state: the single source of truth shared by the
 * Expo module, which owns the audio engine, and [MetronomeService], which holds the
 * process at foreground priority and draws the notification.
 *
 * The service deliberately does not own the engine: `oom_adj` is assigned per process,
 * not per component, so a foreground service anywhere in the process protects the Oboe
 * audio thread too. Keeping the engine in the module avoids rebuilding its lifecycle
 * around a binder.
 *
 * The state is one snapshot swapped atomically rather than a set of independent
 * `@Volatile` fields. `@Volatile` prevents a torn read *within* one field, never across
 * several: a reader that catches half of an update could see "paused" from the new state
 * and "no notification options" from the old, conclude there is nothing to draw, and
 * leave playback suspended with no way back.
 */
internal object MetronomeSession {

    /** Invoked on the main looper with the snapshots either side of one transition. */
    fun interface Listener {
        fun onChange(old: MetronomeState, new: MetronomeState)
    }

    private val current = AtomicReference(MetronomeState())
    private val listeners = CopyOnWriteArrayList<Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())

    val state: MetronomeState get() = current.get()

    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /**
     * A restart is a single transition, not stop-then-start: the intermediate `Stopped`
     * would tear the foreground service down and put it straight back up, so the
     * notification would blink on every `start()`.
     *
     * It carries no reason because JS asked for it and does not need telling.
     */
    fun start(bpm: Double, background: BackgroundOptions?) = mutate {
        MetronomeState(
            playback = Playback.Running,
            bpm = bpm,
            background = background,
            // Carried over rather than defaulted: where the app is has nothing to do
            // with playback, and the activity reports it only when it changes.
            appInForeground = it.appInForeground
        )
    }

    /**
     * Driven by the activity lifecycle. It is a session field rather than something the
     * service works out for itself so that the notification is rendered from one
     * snapshot — the same rule that keeps "paused" and "no options" from being read
     * from different halves of an update.
     */
    fun setForeground(inForeground: Boolean) = mutate {
        if (it.appInForeground == inForeground) it else it.copy(appInForeground = inForeground)
    }

    /** Applied in every state, including `Stopped`, so the next `start()` is not needed to bank it. */
    fun setBpm(bpm: Double) = mutate { if (it.bpm == bpm) it else it.copy(bpm = bpm) }

    fun pause(reason: String?) = mutate {
        if (it.playback == Playback.Running) {
            it.copy(playback = Playback.Paused, reason = reason)
        } else {
            it
        }
    }

    fun resume(reason: String?) = mutate {
        if (it.playback == Playback.Paused) {
            it.copy(playback = Playback.Running, reason = reason)
        } else {
            it
        }
    }

    /**
     * Drops the notification options with the same transition that ends playback — the
     * two used to be cleared from separate places and could disagree in between.
     *
     * `reason == null` transitions silently, for when JS is already gone.
     */
    fun stop(reason: String?) = mutate {
        if (it.playback == Playback.Stopped) {
            it
        } else {
            MetronomeState(
                playback = Playback.Stopped,
                bpm = it.bpm,
                appInForeground = it.appInForeground,
                reason = reason
            )
        }
    }

    /**
     * A reducer that hands back its input means "nothing happened", and nothing is
     * dispatched — a double tap in the notification shade must not look like two
     * transitions to a consumer that counts them.
     */
    private fun mutate(reduce: (MetronomeState) -> MetronomeState) {
        while (true) {
            val old = current.get()
            val new = reduce(old)
            if (new === old) return
            if (current.compareAndSet(old, new)) {
                // The main looper is the one ordering both the service and sendEvent()
                // agree on, and it is where onStop was already posted. The cost is one
                // looper tick before the shade updates, which is imperceptible.
                mainHandler.post { listeners.forEach { it.onChange(old, new) } }
                return
            }
        }
    }

    /** The session outlives any single test, so each one has to hand it back empty. */
    @VisibleForTesting
    fun reset() {
        listeners.clear()
        current.set(MetronomeState())
    }
}
