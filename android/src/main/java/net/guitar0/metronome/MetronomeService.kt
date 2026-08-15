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

        // Promote unconditionally, before deciding anything else. Every delivery here
        // is armed by startForegroundService(), and returning from onStartCommand()
        // without startForeground() is a ForegroundServiceDidNotStartInTimeException —
        // including when the reason to bail is that playback has already ended. That
        // case is reachable: the engine can fail or be interrupted between start()
        // launching the service and the service being handed the intent, which clears
        // the options from the main thread. Defaults stand in for that window; the
        // notification is torn down again by stopSelf() a few lines down.
        promoteToForeground(state)

        when {
            state.background == null -> stopSelf()

            intent?.action == ACTION_STOP -> {
                MetronomeSession.stop(STOP_REASON_NOTIFICATION)
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
            NotificationFactory.build(this, options, state.bpm),
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
     * under a caller-supplied `text` changes nothing the user can see. Redrawing anyway
     * is not merely wasteful — `NotificationManagerService` rate-limits a package at
     * roughly five updates a second and then silently drops the rest, which leaves the
     * notification stuck on an arbitrary value.
     *
     * Options are compared by identity because every `start()` builds a fresh record, so
     * a restart always redraws while a tempo change never rebuilds them.
     */
    @SuppressLint("MissingPermission")
    private fun onSessionChange(old: MetronomeState, new: MetronomeState) {
        if (!promoted) return

        // Playback is over, or was never backgrounded: the module tears the service down
        // on the same transition, and drawing here would race that.
        val options = new.background ?: return
        if (new.playback == Playback.Stopped) return

        if (old.background === options &&
            NotificationFactory.contentText(options, old.bpm) ==
            NotificationFactory.contentText(options, new.bpm)
        ) {
            return
        }

        val manager = NotificationManagerCompat.from(this)
        // Without the permission the post is dropped by the system anyway.
        if (!manager.areNotificationsEnabled()) return
        manager.notify(
            NotificationFactory.NOTIFICATION_ID,
            NotificationFactory.build(this, options, new.bpm)
        )
    }

    companion object {
        const val ACTION_START = "net.guitar0.metronome.action.START"
        const val ACTION_STOP = "net.guitar0.metronome.action.STOP"

        const val STOP_REASON_NOTIFICATION = "notification"

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
