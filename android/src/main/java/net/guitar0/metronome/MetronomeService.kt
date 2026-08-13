package net.guitar0.metronome

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat

/**
 * Foreground service of type `mediaPlayback`. It exists purely to raise the
 * process out of the `cached` bucket so the OS stops treating it as the first
 * candidate for the low-memory killer — the audio engine itself lives in the
 * Expo module, see [MetronomeEngineHolder].
 *
 * Declared in the consumer's manifest only when the config plugin runs with
 * `backgroundAudio: true`.
 */
class MetronomeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val options = MetronomeEngineHolder.backgroundOptions

        // Promote unconditionally, before deciding anything else. Every delivery here
        // is armed by startForegroundService(), and returning from onStartCommand()
        // without startForeground() is a ForegroundServiceDidNotStartInTimeException —
        // including when the reason to bail is that playback has already ended. That
        // case is reachable: the engine can fail or be interrupted between start()
        // launching the service and the service being handed the intent, which nulls
        // the options from the main thread. Defaults stand in for that window; the
        // notification is torn down again by stopSelf() a few lines down.
        promoteToForeground(options ?: BackgroundOptions())

        when {
            options == null -> stopSelf()

            intent?.action == ACTION_STOP -> {
                MetronomeEngineHolder.stop(STOP_REASON_NOTIFICATION)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun promoteToForeground(options: BackgroundOptions) {
        NotificationFactory.createChannel(this, options.channelName)
        ServiceCompat.startForeground(
            this,
            NotificationFactory.NOTIFICATION_ID,
            NotificationFactory.build(this, options, MetronomeEngineHolder.bpm),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    /** Swiping the app away tears down the JS context, so stop silently. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        MetronomeEngineHolder.stop(null)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "net.guitar0.metronome.action.START"
        const val ACTION_UPDATE = "net.guitar0.metronome.action.UPDATE"
        const val ACTION_STOP = "net.guitar0.metronome.action.STOP"

        const val STOP_REASON_NOTIFICATION = "notification"

        fun intent(context: Context, action: String? = null): Intent =
            Intent(context, MetronomeService::class.java).apply { this.action = action }
    }
}
