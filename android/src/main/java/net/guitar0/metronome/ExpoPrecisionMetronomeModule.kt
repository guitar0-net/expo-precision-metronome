package net.guitar0.metronome

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

private const val BPM_MIN = 20.0
private const val BPM_MAX = 300.0

class ExpoPrecisionMetronomeModule : Module() {
    private var engine: MetronomeEngine? = null

    override fun definition() = ModuleDefinition {
        Name("ExpoPrecisionMetronome")

        Events("onBeat", "onStop")

        OnCreate {
            val context =
                requireNotNull(
                    appContext.currentActivity?.applicationContext
                        ?: appContext.reactContext?.applicationContext
                ) { "Application context not available" }
            engine = MetronomeEngine(context) { eventName, payload ->
                sendEvent(eventName, payload)
            }
        }

        OnDestroy {
            engine?.stop(null)
            engine?.destroy()
            engine = null
        }

        AsyncFunction("start") { bpm: Double ->
            if (bpm < BPM_MIN || bpm > BPM_MAX) {
                throw IllegalArgumentException(
                    "BPM must be between ${BPM_MIN.toInt()} and ${BPM_MAX.toInt()}, got $bpm"
                )
            }
            engine?.start(bpm)
        }

        AsyncFunction("stop") {
            engine?.stop("explicit")
        }

        AsyncFunction("setBpm") { bpm: Double ->
            if (bpm < BPM_MIN || bpm > BPM_MAX) {
                throw IllegalArgumentException(
                    "BPM must be between ${BPM_MIN.toInt()} and ${BPM_MAX.toInt()}, got $bpm"
                )
            }
            engine?.setBpm(bpm)
        }
    }
}
