package net.guitar0.metronome

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record
import expo.modules.kotlin.types.Enumerable

private const val BPM_MIN = 20.0
private const val BPM_MAX = 300.0

enum class SoundPreset : Enumerable {
    click,
    beep,
    woodblock,
    rim,
    hihat,
    cowbell
}

enum class BeatAccent : Enumerable {
    strong,
    normal,
    muted
}

class BackgroundOptions : Record {
    @Field var title: String = "Metronome"

    /** `null` renders "{bpm} BPM" and follows setBpm(). */
    @Field var text: String? = null

    @Field var icon: String? = null

    @Field var color: String? = null

    @Field var stopLabel: String = "Stop"

    @Field var showStopButton: Boolean = true

    @Field var channelName: String = "Metronome"

    /** One of "public", "private", "secret". Validated in JS. */
    @Field var lockscreenVisibility: String = "public"
}

class StartOptions : Record {
    /** Non-null enables background playback; requires the config plugin. */
    @Field var background: BackgroundOptions? = null

    @Field var mixWithOthers: Boolean = false
}

class BackgroundNotConfiguredException :
    CodedException(
        "Background playback requires a foreground service declaration. " +
            "Add the config plugin to your app config: " +
            "[\"expo-precision-metronome\", { \"backgroundAudio\": true }]"
    )

class ExpoPrecisionMetronomeModule : Module() {
    private var engine: MetronomeEngine? = null
    private var androidContext: Context? = null

    override fun definition() = ModuleDefinition {
        Name("ExpoPrecisionMetronome")

        Events("onBeat", "onStop")

        OnCreate {
            val context =
                requireNotNull(
                    appContext.currentActivity?.applicationContext
                        ?: appContext.reactContext?.applicationContext
                ) { "Application context not available" }
            this@ExpoPrecisionMetronomeModule.androidContext = context

            val newEngine = MetronomeEngine(context) { eventName, payload ->
                // Every stop path funnels through here — explicit, audio focus loss,
                // native error, notification button — so the service is torn down once.
                if (eventName == "onStop") {
                    stopBackgroundService()
                }
                sendEvent(eventName, payload)
            }
            MetronomeEngineHolder.attach(newEngine)
            engine = newEngine
        }

        OnDestroy {
            engine?.let {
                it.stop(null)
                it.destroy()
                MetronomeEngineHolder.detach(it)
            }
            stopBackgroundService()
            engine = null
            androidContext = null
        }

        AsyncFunction("start") { bpm: Double, options: StartOptions? ->
            assertBpm(bpm)

            val background = options?.background
            if (background != null) {
                requireBackgroundConfigured()
            }

            MetronomeEngineHolder.backgroundOptions = background
            MetronomeEngineHolder.bpm = bpm
            engine?.start(bpm, options?.mixWithOthers ?: false)

            if (background != null) {
                startBackgroundService()
            }
        }

        AsyncFunction("stop") {
            engine?.stop("explicit")
        }

        AsyncFunction("setBpm") { bpm: Double ->
            assertBpm(bpm)
            MetronomeEngineHolder.bpm = bpm
            engine?.setBpm(bpm)
            // Only the generated "{bpm} BPM" text tracks the tempo; a caller-supplied
            // text stays as it is, so there is nothing to redraw.
            if (MetronomeEngineHolder.backgroundOptions?.text == null) {
                updateBackgroundService()
            }
        }

        AsyncFunction("setSound") { preset: SoundPreset ->
            engine?.setSound(preset.ordinal)
        }

        AsyncFunction("setPattern") { pattern: List<BeatAccent> ->
            engine?.setPattern(MetronomeEngine.encodePattern(pattern))
        }
    }

    private fun assertBpm(bpm: Double) {
        if (bpm < BPM_MIN || bpm > BPM_MAX) {
            throw IllegalArgumentException(
                "BPM must be between ${BPM_MIN.toInt()} and ${BPM_MAX.toInt()}, got $bpm"
            )
        }
    }

    /**
     * The service is only declared when the config plugin ran, so resolving it is a
     * direct check that the consumer opted in.
     */
    private fun requireBackgroundConfigured() {
        val context = androidContext ?: throw BackgroundNotConfiguredException()
        val intent = MetronomeService.intent(context)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveService(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveService(intent, 0)
        }
        if (resolved == null) {
            throw BackgroundNotConfiguredException()
        }
    }

    private fun startBackgroundService() {
        val context = androidContext ?: return
        // Called while the app is in the foreground, so the Android 12 restriction
        // on starting foreground services from the background does not apply.
        ContextCompat.startForegroundService(
            context,
            MetronomeService.intent(context, MetronomeService.ACTION_START)
        )
    }

    private fun updateBackgroundService() {
        val context = androidContext ?: return
        if (MetronomeEngineHolder.backgroundOptions == null) return
        ContextCompat.startForegroundService(
            context,
            MetronomeService.intent(context, MetronomeService.ACTION_UPDATE)
        )
    }

    private fun stopBackgroundService() {
        val context = androidContext ?: return
        MetronomeEngineHolder.backgroundOptions = null
        context.stopService(MetronomeService.intent(context))
    }
}
