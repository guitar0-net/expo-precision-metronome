package net.guitar0.metronome

/** Stands in for [MetronomeEngine] so the holder and the service can be tested without JNI. */
internal class FakePlaybackController(override var isRunning: Boolean = true) : PlaybackController {
    val stopReasons = mutableListOf<String?>()

    override fun stop(reason: String?) {
        stopReasons += reason
        isRunning = false
    }
}
