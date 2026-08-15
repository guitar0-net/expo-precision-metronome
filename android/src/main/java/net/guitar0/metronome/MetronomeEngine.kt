package net.guitar0.metronome

import android.content.Context
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

internal class MetronomeEngine(
    context: Context,
    private val onEvent: (eventName: String, payload: Map<String, Any>) -> Unit
) {
    private var nativeHandle: Long = 0
    private val appContext = context.applicationContext
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)

    /**
     * Set when the system took focus transiently — an incoming call. It is the only
     * pause `AUDIOFOCUS_GAIN` may undo, and the only one that must leave the focus
     * request standing: abandoning it forfeits the very callback that says the call
     * ended, and playback would sit paused forever.
     */
    private val pausedByTransientLoss = AtomicBoolean(false)

    private var focusRequest: AudioFocusRequest? = null
    private var legacyFocusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var noisyReceiver: BecomingNoisyReceiver? = null

    /** Remembered from [start] so [resume] can ask for the focus type it had. */
    private var mixWithOthers = false

    init {
        nativeHandle = nativeCreate()
    }

    /**
     * No-op on an already-running stream — callers that need the new [bpm] or
     * [mixWithOthers] to take effect must [stop] first. Returns whether the stream
     * was actually opened, so the caller can tell the two cases apart.
     */
    fun start(bpm: Double, mixWithOthers: Boolean = false): Boolean {
        if (!running.compareAndSet(false, true)) return false
        this.mixWithOthers = mixWithOthers
        paused.set(false)
        requestAudioFocus(mixWithOthers)
        registerNoisyReceiver()
        nativeStart(nativeHandle, bpm)
        return true
    }

    /**
     * Silences the stream instead of closing it, so [resume] re-enters with the
     * latency and sample accuracy of a stream that never stopped.
     *
     * Focus is released and the noisy receiver unregistered: holding focus while
     * producing nothing keeps a backing track ducked for no reason, which is exactly
     * what `mixWithOthers` exists to avoid, and a receiver whose whole job is
     * "stop when the sound would leak" has nothing to do while there is no sound.
     *
     * Returns whether this call was the one that paused.
     */
    fun pause(): Boolean {
        if (!running.get()) return false
        if (!paused.compareAndSet(false, true)) return false
        // Silence first, so nothing is still being written after focus is handed back.
        nativeSetPaused(nativeHandle, true)
        unregisterNoisyReceiver()
        if (!pausedByTransientLoss.get()) {
            releaseAudioFocus()
        }
        return true
    }

    /** Restarts the bar — the scheduler resets on the audio thread. */
    fun resume(): Boolean {
        if (!running.get()) return false
        if (!paused.compareAndSet(true, false)) return false
        pausedByTransientLoss.set(false)
        // Focus first, for the mirror-image reason: no sound before we may make it.
        // Already held when the system is handing it back after a call.
        if (!focusHeld) {
            requestAudioFocus(mixWithOthers)
        }
        registerNoisyReceiver()
        nativeSetPaused(nativeHandle, false)
        return true
    }

    /**
     * `reason == null` stops without reporting anything — the caller either already
     * knows (a restart) or has no one left to tell.
     */
    fun stop(reason: String?) {
        if (!running.compareAndSet(true, false)) return
        // Pausing already released focus and the receiver, so both teardowns below are
        // no-ops in that case — but the flag has to go, or the next start() would
        // inherit a pause nobody asked for.
        paused.set(false)
        pausedByTransientLoss.set(false)
        nativeStop(nativeHandle)
        unregisterNoisyReceiver()
        releaseAudioFocus()
        if (reason != null) {
            report("onStop", reason)
        }
    }

    /**
     * The engine never mutates playback state itself — it reports the cause and the
     * module turns that into one session transition, so an interruption, the
     * notification and JS all take the same path.
     */
    private fun report(signal: String, reason: String) {
        mainHandler.post { onEvent(signal, mapOf("reason" to reason)) }
    }

    fun setBpm(bpm: Double) {
        nativeSetBpm(nativeHandle, bpm)
    }

    fun setSound(presetIndex: Int) {
        nativeSetSound(nativeHandle, presetIndex)
    }

    fun setPattern(encoded: Long) {
        nativeSetPattern(nativeHandle, encoded)
    }

    fun destroy() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0
        }
    }

    // Called from JNI on the Oboe audio thread. accentOrdinal: 0=strong, 1=normal, 2=muted.
    @Keep
    fun onBeat(beat: Int, timestamp: Double, accentOrdinal: Int) {
        val accent = when (accentOrdinal) {
            0 -> "strong"
            2 -> "muted"
            else -> "normal"
        }
        mainHandler.post {
            onEvent("onBeat", mapOf("beat" to beat, "timestamp" to timestamp, "accent" to accent))
        }
    }

    // Called from JNI when the Oboe stream encounters an unrecoverable error (e.g. device disconnect).
    // The C++ running_ flag is already false at this point, so nativeStop() is a no-op.
    @Keep
    fun onNativeStop(reason: String) {
        stop(reason)
    }

    /**
     * `mixWithOthers` asks for MAY_DUCK focus instead of exclusive gain: a backing
     * track in another app keeps playing, just quieter. Focus is still requested
     * either way, so a phone call still interrupts the metronome.
     *
     * The two kinds of loss are answered differently, following the platform
     * convention rather than collapsing both into a stop:
     *
     * - `LOSS_TRANSIENT` is a call. Pause, keep the focus request, and come back on
     *   `GAIN` — the metronome the user was practising with is still the thing they
     *   were doing.
     * - `LOSS` is another app taking over for good. Stop; there is nothing to come
     *   back from, and no `GAIN` will arrive.
     */
    private fun requestAudioFocus(mixWithOthers: Boolean) {
        val gain = if (mixWithOthers) {
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        } else {
            AudioManager.AUDIOFOCUS_GAIN
        }

        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            when (AudioFocusPolicy.outcome(change, paused.get(), pausedByTransientLoss.get())) {
                FocusOutcome.Stop -> stop(REASON_INTERRUPTION)

                // Recorded before reporting, because pause() reads it to decide whether
                // the focus request may be abandoned.
                FocusOutcome.Pause -> {
                    pausedByTransientLoss.set(true)
                    report("onPause", REASON_INTERRUPTION)
                }

                FocusOutcome.Resume -> report("onResume", REASON_INTERRUPTION)

                FocusOutcome.Ignore -> Unit
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(gain)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(listener)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, gain)
            legacyFocusListener = listener
        }
    }

    /**
     * Unplugging headphones pauses and is never auto-resumed: the user pulled the
     * plug, so the silence is what they asked for. Coming back is a deliberate act,
     * from the notification or from the app.
     */
    private fun registerNoisyReceiver() {
        val receiver = BecomingNoisyReceiver { report("onPause", REASON_INTERRUPTION) }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        noisyReceiver = receiver
    }

    private fun unregisterNoisyReceiver() {
        noisyReceiver?.let { appContext.unregisterReceiver(it) }
        noisyReceiver = null
    }

    /** Whether a focus request is outstanding, whatever the API level put it in. */
    private val focusHeld: Boolean
        get() = focusRequest != null || legacyFocusListener != null

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            legacyFocusListener?.let { audioManager.abandonAudioFocus(it) }
            legacyFocusListener = null
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeStart(handle: Long, bpm: Double)
    private external fun nativeStop(handle: Long)
    private external fun nativeSetPaused(handle: Long, paused: Boolean)
    private external fun nativeSetBpm(handle: Long, bpm: Double)
    private external fun nativeSetSound(handle: Long, presetIndex: Int)
    private external fun nativeSetPattern(handle: Long, encoded: Long)

    companion object {
        private const val REASON_INTERRUPTION = "interruption"

        // Encoding: bits 32-36 = (length-1), bits 0-31 = 16×2-bit accent codes.
        // 0=strong, 1=normal, 2=muted.
        fun encodePattern(pattern: List<BeatAccent>): Long {
            val len = (pattern.size - 1).toLong() shl 32
            var bits = 0L
            pattern.forEachIndexed { i, accent ->
                val code: Long = when (accent) {
                    BeatAccent.strong -> 0L
                    BeatAccent.normal -> 1L
                    BeatAccent.muted -> 2L
                }
                bits = bits or (code shl (i * 2))
            }
            return len or bits
        }

        init {
            System.loadLibrary("metronome")
        }
    }
}
