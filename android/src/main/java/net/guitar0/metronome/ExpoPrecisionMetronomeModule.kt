package net.guitar0.metronome

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import expo.modules.interfaces.permissions.PermissionsStatus
import expo.modules.kotlin.Promise
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

    /** Shown while playback is running; tapping it suspends the metronome. */
    @Field var pauseLabel: String = "Pause"

    /** Shown in its place while playback is paused. */
    @Field var resumeLabel: String = "Resume"

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

class PermissionsUnavailableException :
    CodedException(
        "The Expo permissions manager is not available. Are all the installed Expo " +
            "modules properly linked?"
    )

class NoForegroundActivityException :
    CodedException(
        "requestNotificationPermission() needs an activity on screen to show the " +
            "system dialog. Call it from a screen the user is looking at, not from a " +
            "background task."
    )

class NotificationPermissionNotDeclaredException :
    CodedException(
        "POST_NOTIFICATIONS is missing from AndroidManifest.xml. Android denies a " +
            "permission the manifest never declared without showing anything, and " +
            "reports it as permanently denied from then on. The config plugin adds " +
            "it — [\"expo-precision-metronome\", { \"backgroundAudio\": true }] — or " +
            "declare it by hand next to the MetronomeService declaration."
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
                // "onStop", "onPause" and "onResume" here are the engine's own signals,
                // not JS events: it reports what the system did to it — focus lost to a
                // call and handed back, headphones unplugged, a native stream error.
                // Routing them through the session means every cause, including the
                // notification button and JS, produces one transition and one
                // onPlaybackChange.
                val reason = payload["reason"] as? String
                when (eventName) {
                    "onStop" -> MetronomeSession.stop(reason)
                    "onPause" -> MetronomeSession.pause(reason)
                    "onResume" -> MetronomeSession.resume(reason)
                    else -> sendEvent(eventName, payload)
                }
            }
            engine = newEngine

            val listener = MetronomeSession.Listener { old, new -> onSessionChange(old, new) }
            sessionListener = listener
            MetronomeSession.addListener(listener)
        }

        // What the notification renders depends on whether the user can already see the
        // tempo in the app itself. The module writes the flag into the session and the
        // service reacts to it, so there is still exactly one owner of the notification.
        OnActivityEntersForeground {
            MetronomeSession.setForeground(true)
        }

        OnActivityEntersBackground {
            MetronomeSession.setForeground(false)
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
                warnIfNotificationHidden()
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

        // The library never prompts on its own: an automatic dialog at the first
        // start({ background }) lands at a moment the app cannot predict — mid-lesson,
        // say — and on Android 13 a second denial means it never appears again. When to
        // ask is a product decision, so it is handed to the app as a call it makes.
        AsyncFunction("requestNotificationPermission") { promise: Promise ->
            val permission = runtimeNotificationPermission()
            if (permission == null) {
                promise.resolve(true)
                return@AsyncFunction
            }
            // Rejected rather than answered `false`. Without an activity the platform
            // shows nothing, but Expo banks the permission as asked regardless — which
            // moves it from "undetermined" to "denied" for good over a dialog that
            // never appeared. A caller told "no" cannot distinguish that from a real
            // denial; one told "not now" can try again from a screen.
            if (appContext.currentActivity == null) throw NoForegroundActivityException()

            val permissions = appContext.permissions ?: throw PermissionsUnavailableException()
            // Checked before asking rather than left to fail: Expo banks the permission
            // as asked, while a permission the manifest never declared is denied with no
            // dialog — together pinning it to "denied", canAskAgain false, for the life
            // of the install over a prompt nobody saw.
            if (!permissions.isPermissionPresentInManifest(permission)) {
                throw NotificationPermissionNotDeclaredException()
            }
            permissions.askForPermissions(
                { result ->
                    val status = result[permission]?.status
                    promise.resolve(status == PermissionsStatus.GRANTED)
                },
                permission
            )
        }

        // Reports `canAskAgain` beside the status because the status alone cannot carry
        // the decision the caller has to make: Android 13 shows the dialog again after
        // the first denial and only stops after the second, so "denied" spans both a
        // state worth retrying from and one that is final.
        AsyncFunction("getNotificationPermission") { promise: Promise ->
            val permission = runtimeNotificationPermission()
            if (permission == null) {
                promise.resolve(permissionResult(PermissionsStatus.GRANTED, canAskAgain = true))
                return@AsyncFunction
            }
            val permissions = appContext.permissions ?: throw PermissionsUnavailableException()
            permissions.getPermissions(
                { result ->
                    val response = result[permission]
                    promise.resolve(
                        permissionResult(
                            response?.status ?: PermissionsStatus.UNDETERMINED,
                            // Expo reports it only for a denial and defaults the rest to
                            // true, which is the same shape this API promises.
                            canAskAgain = response?.canAskAgain ?: true
                        )
                    )
                },
                permission
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
     *
     * Asked of the notification manager rather than of the permission, because the two
     * can disagree: notifications can be switched off app-wide with POST_NOTIFICATIONS
     * still granted, and what matters here is whether anything will actually appear.
     */
    private fun isNotificationVisible(state: MetronomeState): Boolean {
        if (state.background == null) return false
        val context = androidContext ?: return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** The `NotificationPermission` record as TypeScript declares it. */
    private fun permissionResult(status: PermissionsStatus, canAskAgain: Boolean) =
        mapOf("status" to status.status, "canAskAgain" to canAskAgain)

    /**
     * `null` below Android 13, where posting a notification needs no runtime permission
     * and there is therefore nothing to ask for or report on.
     */
    private fun runtimeNotificationPermission(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
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

    /**
     * A missing notification is no reason to refuse to play — the primary function is
     * not the one that degrades. But since 2.0 the notification is also the only way to
     * pause from outside the app, so the degradation is severe enough to be worth
     * saying out loud.
     *
     * Logcat rather than `console.warn`: a developer wiring up the native side may well
     * not be watching Metro, and the app can read the same fact from
     * `getState().notificationVisible` when it wants to act on it.
     */
    private fun warnIfNotificationHidden() {
        val context = androidContext ?: return
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        Log.w(
            TAG,
            "Background playback was requested but notifications are disabled, so the " +
                "metronome notification is hidden and pause is only reachable from " +
                "inside the app. Call requestNotificationPermission() beforehand."
        )
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
        const val TAG = "ExpoPrecisionMetronome"
    }
}
