package net.guitar0.metronome

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat

/**
 * Foreground service of type `mediaPlayback`. It exists purely to raise the
 * process out of the `cached` bucket so the OS stops treating it as the first
 * candidate for the low-memory killer — the audio engine itself lives in the
 * Expo module, see [MetronomeSession].
 *
 * It is also the only writer of the ongoing notification: it subscribes to the
 * session and redraws from the snapshots it is handed.
 *
 * Declared in the consumer's manifest only when the config plugin runs with
 * `backgroundAudio: true`.
 */
class MetronomeService : Service() {

    private val sessionListener = MetronomeSession.Listener { old, new ->
        onSessionChange(old, new)
    }

    /** Nothing may be drawn before [startForeground], so redraws wait for the first command. */
    private var promoted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        MetronomeSession.addListener(sessionListener)
    }

    override fun onDestroy() {
        MetronomeSession.removeListener(sessionListener)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = MetronomeSession.state
        val action = intent?.action

        // Promote before deciding anything else. A delivery armed by
        // startForegroundService() that returns from onStartCommand() without
        // startForeground() is a ForegroundServiceDidNotStartInTimeException —
        // including when the reason to bail is that playback has already ended. That
        // case is reachable: the engine can fail or be interrupted between start()
        // launching the service and the service being handed the intent, which clears
        // the options from the main thread. Defaults stand in for that window; the
        // notification is torn down again by stopSelf() a few lines down.
        //
        // Pause and resume are the exception. They arrive through
        // PendingIntent.getService(), a plain startService() that arms no deadline, and
        // the transition they cause is redrawn by the listener below with the new
        // button label. Promoting as well would post the same notification twice per
        // tap — straight towards the rate limit the whole update strategy exists to
        // stay under. A pause reaching an instance that has never promoted is still
        // promoted, because until then there is nothing for the listener to redraw.
        if (!promoted || (action != ACTION_PAUSE && action != ACTION_RESUME)) {
            promoteToForeground(state)
        }

        // Separate actions rather than one toggle: the shade and the app's own UI issue
        // commands independently, so a tap that lost the race has to be a no-op instead
        // of the opposite action. The session decides whether anything happened, which
        // is why nothing here consults the playback state first.
        when {
            state.background == null -> stopSelf()

            action == ACTION_PAUSE -> MetronomeSession.pause(REASON_NOTIFICATION)

            action == ACTION_RESUME -> MetronomeSession.resume(REASON_NOTIFICATION)

            action == ACTION_STOP -> {
                MetronomeSession.stop(REASON_NOTIFICATION)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    /** Swiping the app away tears down the JS context, so stop silently. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        MetronomeSession.stop(null)
        stopSelf()
    }

    private fun promoteToForeground(state: MetronomeState) {
        val options = state.background ?: BackgroundOptions()
        NotificationFactory.createChannel(this, options.channelName)
        ServiceCompat.startForeground(
            this,
            NotificationFactory.NOTIFICATION_ID,
            NotificationFactory.build(this, options, state),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        promoted = true
    }

    /**
     * Redraws in place rather than going back through `startForegroundService()`, which
     * is an ActivityManager round-trip per update while a BPM slider produces one per
     * frame.
     *
     * Deduplicated on the rendered content, not on the inputs behind it: a tempo change
     * while the app is on screen, or under a caller-supplied `text`, changes nothing the
     * user can see — and comparing the two snapshots is enough to know it. Redrawing
     * anyway is not merely wasteful — `NotificationManagerService` rate-limits a package
     * at roughly five updates a second and then silently drops the rest, which leaves
     * the notification stuck on an arbitrary value.
     *
     * Options are compared by identity because every `start()` builds a fresh record, so
     * a restart always redraws while a tempo change never rebuilds them.
     *
     * A change of [Playback] is exempt from all of that and always redraws: it swaps the
     * button label, and a visible lag between tapping Pause and the label changing reads
     * as "it didn't work". Not every such change is a button press — an interruption
     * arrives the same way — but interruptions come in ones, not at slider rates, so the
     * exemption stays well clear of the limit.
     */
    @SuppressLint("MissingPermission")
    private fun onSessionChange(old: MetronomeState, new: MetronomeState) {
        if (!promoted) return

        // Playback is over, or was never backgrounded: the module tears the service down
        // on the same transition, and drawing here would race that.
        val options = new.background ?: return
        if (new.playback == Playback.Stopped) return

        if (old.playback == new.playback &&
            old.background === options &&
            NotificationFactory.contentText(options, old) ==
            NotificationFactory.contentText(options, new)
        ) {
            return
        }

        val manager = NotificationManagerCompat.from(this)
        // Without the permission the post is dropped by the system anyway.
        if (!manager.areNotificationsEnabled()) return
        manager.notify(
            NotificationFactory.NOTIFICATION_ID,
            NotificationFactory.build(this, options, new)
        )
    }

    companion object {
        const val ACTION_START = "net.guitar0.metronome.action.START"
        const val ACTION_STOP = "net.guitar0.metronome.action.STOP"
        const val ACTION_PAUSE = "net.guitar0.metronome.action.PAUSE"
        const val ACTION_RESUME = "net.guitar0.metronome.action.RESUME"

        const val REASON_NOTIFICATION = "notification"

        /**
         * The class argument already pins the destination, but that guarantee is invisible
         * to static analysis once it passes through `apply {}`. Setting the package restates
         * it plainly: this intent is wrapped in a PendingIntent handed to the notification
         * shade, and it must never be deliverable by another app.
         */
        fun intent(context: Context, action: String? = null): Intent {
            val intent = Intent(context, MetronomeService::class.java)
            intent.setPackage(context.packageName)
            intent.action = action
            return intent
        }
    }
}
