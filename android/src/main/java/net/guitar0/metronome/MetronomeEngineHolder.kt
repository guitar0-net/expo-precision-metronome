package net.guitar0.metronome

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
    private var engine: MetronomeEngine? = null

    /** Non-null only while background playback is active — the service reads it to build its notification. */
    @Volatile
    var backgroundOptions: BackgroundOptions? = null

    @Volatile
    var bpm: Double = 0.0

    fun attach(engine: MetronomeEngine) {
        this.engine = engine
    }

    fun detach(engine: MetronomeEngine) {
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
