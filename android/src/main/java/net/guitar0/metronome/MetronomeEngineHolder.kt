package net.guitar0.metronome

/**
 * The slice of the audio engine that [MetronomeEngineHolder] and [MetronomeService]
 * depend on. Narrow on purpose: it keeps the holder free of JNI so its state machine
 * can be exercised by plain JVM unit tests.
 */
internal interface PlaybackController {
    val isRunning: Boolean
    fun stop(reason: String?)
}

/**
 * Process-wide bridge between the Expo module, which owns the engine, and
 * [MetronomeService], which only holds the process at foreground priority.
 *
 * The service deliberately does not own the engine: `oom_adj` is assigned per
 * process, not per component, so a foreground service anywhere in the process
 * protects the Oboe audio thread too. Keeping the engine in the module avoids
 * rebuilding its lifecycle around a binder.
 */
internal object MetronomeEngineHolder {
    @Volatile
    private var engine: PlaybackController? = null

    /** Non-null only while background playback is active — the service reads it to build its notification. */
    @Volatile
    var backgroundOptions: BackgroundOptions? = null

    @Volatile
    var bpm: Double = 0.0

    val isRunning: Boolean get() = engine?.isRunning == true

    fun attach(engine: PlaybackController) {
        this.engine = engine
    }

    fun detach(engine: PlaybackController) {
        if (this.engine === engine) {
            this.engine = null
            backgroundOptions = null
        }
    }

    /** `reason == null` stops without emitting `onStop` — used when JS is already gone. */
    fun stop(reason: String?) {
        engine?.stop(reason)
    }
}
