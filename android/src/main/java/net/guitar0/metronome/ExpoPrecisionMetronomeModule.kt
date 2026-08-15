package net.guitar0.metronome

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
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
        "Background playback requires the MetronomeService declaration and its " +
            "foreground service permissions. Add the config plugin to your app config — " +
            "[\"expo-precision-metronome\", { \"backgroundAudio\": true }] — then run " +
            "`npx expo prebuild`. Projects that never prebuild must declare them in " +
            "AndroidManifest.xml by hand."
    )

class ExpoPrecisionMetronomeModule : Module() {
    private var engine: MetronomeEngine? = null
    private var androidContext: Context? = null
    private var sessionListener: MetronomeSession.Listener? = null

    override fun definition() = ModuleDefinition {
        Name("ExpoPrecisionMetronome")

        Events("onBeat", "onPlaybackChange")

        OnCreate {
            val context =
                requireNotNull(
                    appContext.currentActivity?.applicationContext
                        ?: appContext.reactContext?.applicationContext
                ) { "Application context not available" }
            this@ExpoPrecisionMetronomeModule.androidContext = context

            val newEngine = MetronomeEngine(context) { eventName, payload ->
                // "onStop" here is the engine's own signal, not a JS event: it reports
                // only the stops it decided on itself — audio focus loss, headphones
                // unplugged, a native stream error. Routing them through the session
                // means every cause, including the notification button and JS, produces
                // one transition and one onPlaybackChange.
                if (eventName == "onStop") {
                    MetronomeSession.stop(payload["reason"] as? String)
                } else {
                    sendEvent(eventName, payload)
                }
            }
            engine = newEngine

            val listener = MetronomeSession.Listener { old, new -> onSessionChange(old, new) }
            sessionListener = listener
            MetronomeSession.addListener(listener)
        }

        OnDestroy {
            // Unsubscribed first: what follows is teardown, not a transition anyone is
            // owed an event for.
            sessionListener?.let { MetronomeSession.removeListener(it) }
            sessionListener = null
            engine?.let {
                it.stop(null)
                it.destroy()
            }
            // The session outlives the module, so it has to be told that the engine it
            // describes is gone — otherwise a reload lands on a snapshot claiming
            // playback that no engine is producing.
            MetronomeSession.stop(null)
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

            // MetronomeEngine.start() is a no-op on a live stream, so calling start()
            // twice would otherwise leave the session describing a tempo and a set of
            // options that playback never picked up. Tear the stream down first so every
            // start() means the same thing. Silent (`null`), because JS asked for a
            // restart, not for a stop — and because the restart is one session
            // transition, never a stop followed by a start.
            engine?.stop(null)
            engine?.start(bpm, options?.mixWithOthers ?: false)

            MetronomeSession.start(bpm, background)
        }

        AsyncFunction("stop") {
            MetronomeSession.stop(REASON_EXPLICIT)
        }

        // Idempotent, and deliberately not a toggle: the notification and the app's own
        // UI are independent command sources, so a lost race has to stay harmless
        // rather than invert into the opposite action. The session decides whether
        // anything happened at all.
        AsyncFunction("pause") {
            MetronomeSession.pause(REASON_EXPLICIT)
        }

        AsyncFunction("resume") {
            MetronomeSession.resume(REASON_EXPLICIT)
        }

        // The snapshot is already the single source of truth, so this is a read of it
        // rather than a poll of the engine — which is what makes it safe to call from a
        // component that mounted after the transition it missed.
        AsyncFunction("getState") {
            val state = MetronomeSession.state
            mapOf(
                "state" to state.playback.jsName,
                "bpm" to state.bpm,
                "notificationVisible" to isNotificationVisible(state)
            )
        }

        AsyncFunction("setBpm") { bpm: Double ->
            assertBpm(bpm)
            engine?.setBpm(bpm)
            // The service redraws the notification off this transition, and only when
            // the tempo is what it renders.
            MetronomeSession.setBpm(bpm)
        }

        AsyncFunction("setSound") { preset: SoundPreset ->
            engine?.setSound(preset.ordinal)
        }

        AsyncFunction("setPattern") { pattern: List<BeatAccent> ->
            engine?.setPattern(MetronomeEngine.encodePattern(pattern))
        }
    }

    /**
     * The engine and the foreground service are both driven from here, off the snapshots
     * of a single transition, so JS, the notification and an interruption all take the
     * same path.
     */
    private fun onSessionChange(old: MetronomeState, new: MetronomeState) {
        if (new.playback != old.playback) {
            when (new.playback) {
                // Silent: the event for this transition is sent below, from the snapshot.
                Playback.Stopped -> engine?.stop(null)

                Playback.Paused -> engine?.pause()

                // A restart out of Paused already rebuilt the engine unpaused in the
                // start() body, so resume() finds nothing to do and says so.
                Playback.Running -> engine?.resume()
            }
            // A null reason means JS either asked for this itself or is no longer there
            // to hear about it — a restart, or teardown.
            new.reason?.let {
                sendEvent(
                    "onPlaybackChange",
                    mapOf("state" to new.playback.jsName, "reason" to it)
                )
            }
        }
        syncBackgroundService(old, new)
    }

    /**
     * The notification is the only way to reach pause from outside the app, so an app
     * that cannot post one has to be able to tell — see `requestNotificationPermission`.
     */
    private fun isNotificationVisible(state: MetronomeState): Boolean {
        if (state.background == null) return false
        val context = androidContext ?: return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Every service command is issued from a listener callback, so they reach the main
     * looper in transition order. That is what makes a stop decided just before a fresh
     * `start()` harmless: the restart's own transition is queued behind it and puts the
     * service back up, instead of the two racing on separate threads.
     */
    private fun syncBackgroundService(old: MetronomeState, new: MetronomeState) {
        val wanted = new.playback != Playback.Stopped && new.background != null
        val had = old.playback != Playback.Stopped && old.background != null
        when {
            // A restart with different options needs no command: the service is already
            // up and redraws from the same transition.
            wanted && !had -> startBackgroundService()

            !wanted && had -> stopBackgroundService()
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
     * direct check that the consumer opted in. The permissions are checked too: a
     * hand-written manifest can declare the service and omit them, and the failure
     * mode then is a SecurityException from startForeground() rather than a coded
     * error JS can act on.
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

        if (!hasPermission(context, Manifest.permission.FOREGROUND_SERVICE)) {
            throw BackgroundNotConfiguredException()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !hasPermission(context, Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK)
        ) {
            throw BackgroundNotConfiguredException()
        }
    }

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun startBackgroundService() {
        val context = androidContext ?: return
        // Called while the app is in the foreground, so the Android 12 restriction
        // on starting foreground services from the background does not apply.
        ContextCompat.startForegroundService(
            context,
            MetronomeService.intent(context, MetronomeService.ACTION_START)
        )
    }

    private fun stopBackgroundService() {
        val context = androidContext ?: return
        context.stopService(MetronomeService.intent(context))
        // Belt and braces: the service owns the notification and drops it on destroy,
        // but stopService() is asynchronous and a redraw may have posted one while the
        // service was still starting up.
        NotificationManagerCompat.from(context).cancel(NotificationFactory.NOTIFICATION_ID)
    }

    private companion object {
        const val REASON_EXPLICIT = "explicit"
    }
}
