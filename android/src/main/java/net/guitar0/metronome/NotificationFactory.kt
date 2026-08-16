package net.guitar0.metronome

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.toColorInt

internal object NotificationFactory {
    const val CHANNEL_ID = "expo-precision-metronome"
    const val NOTIFICATION_ID = 4212

    private const val PENDING_INTENT_FLAGS =
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private const val REQUEST_STOP = 1
    private const val REQUEST_PAUSE = 2
    private const val REQUEST_RESUME = 3

    /**
     * IMPORTANCE_LOW keeps the notification silent and out of the heads-up area —
     * it is a status indicator, not an alert.
     */
    fun createChannel(context: Context, name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            name,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * Rendered from one snapshot, so no two lines of the notification can describe
     * different moments.
     *
     * The pause/resume pair follows `state.playback` alone, so the button always
     * describes what the next tap will do. There is no flag to turn it off: suspending
     * playback from outside the app is the reason this notification carries buttons at
     * all, and Stop on its own is what `background: true` produced before. The buttons
     * stay in both render modes — their labels change only on a direct user action, so
     * they cause no churn, and dropping them would make the notification change height
     * on every app switch.
     *
     * Actions carry no icons. Since Android 7 the shade renders them as plain text, and
     * the only surfaces that still draw the icon are Wear and `MediaStyle` — and a media
     * session is deliberately not registered, or the metronome would capture the headset
     * play/pause button from the backing track the user is practising along to.
     */
    fun build(context: Context, options: BackgroundOptions, state: MetronomeState): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(options.title)
            .setContentText(contentText(options, state))
            .setSmallIcon(resolveIcon(context, options.icon))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(resolveVisibility(options.lockscreenVisibility))

        options.color?.let { value ->
            runCatching { value.toColorInt() }.getOrNull()?.let { builder.setColor(it) }
        }

        launchIntent(context)?.let { builder.setContentIntent(it) }

        if (state.playback == Playback.Paused) {
            builder.addAction(0, options.resumeLabel, resumeIntent(context))
        } else {
            builder.addAction(0, options.pauseLabel, pauseIntent(context))
        }

        if (options.showStopButton) {
            builder.addAction(0, options.stopLabel, stopIntent(context))
        }

        return builder.build()
    }

    /**
     * The content line as it will be rendered, or `null` for no line at all. Exposed so
     * the service can decide whether a transition is worth a redraw by comparing what
     * the user would actually see, rather than the inputs behind it.
     *
     * The tempo is dropped while the app is on screen. It is the only part of the
     * notification that follows `setBpm()`, and the app is already showing the same
     * number in its own UI — removing it makes a stale reading structurally impossible
     * instead of merely unlikely, including when the shade is pulled down over a live
     * app and the activity never pauses at all.
     *
     * A caller-supplied [BackgroundOptions.text] is static by definition, so it is shown
     * in both modes and never redrawn.
     */
    fun contentText(options: BackgroundOptions, state: MetronomeState): String? =
        options.text ?: if (state.appInForeground) null else defaultText(state.bpm)

    /** Falls back to the app icon; consumers should pass a monochrome drawable via `icon`. */
    private fun resolveIcon(context: Context, name: String?): Int {
        if (!name.isNullOrEmpty()) {
            for (type in listOf("drawable", "mipmap")) {
                val id = context.resources.getIdentifier(name, type, context.packageName)
                if (id != 0) return id
            }
        }
        return context.applicationInfo.icon
    }

    private fun resolveVisibility(value: String): Int = when (value) {
        "private" -> NotificationCompat.VISIBILITY_PRIVATE
        "secret" -> NotificationCompat.VISIBILITY_SECRET
        else -> NotificationCompat.VISIBILITY_PUBLIC
    }

    private fun defaultText(bpm: Double): String {
        val rounded = bpm.toInt()
        val label = if (bpm == rounded.toDouble()) rounded.toString() else bpm.toString()
        return "$label BPM"
    }

    private fun launchIntent(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        // Defence in depth: the resolved intent already carries a launcher component, and
        // restating the package keeps the content intent aimed at this app regardless.
        intent.setPackage(context.packageName)
        return PendingIntent.getActivity(context, 0, intent, PENDING_INTENT_FLAGS)
    }

    private fun stopIntent(context: Context) =
        command(context, MetronomeService.ACTION_STOP, REQUEST_STOP)

    private fun pauseIntent(context: Context) =
        command(context, MetronomeService.ACTION_PAUSE, REQUEST_PAUSE)

    private fun resumeIntent(context: Context) =
        command(context, MetronomeService.ACTION_RESUME, REQUEST_RESUME)

    /**
     * Each action needs its own request code: two PendingIntents that differ only in
     * their action are "equal" to the system, so a shared code would have
     * FLAG_UPDATE_CURRENT rewrite one into the other and both buttons would do the
     * same thing.
     */
    private fun command(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = MetronomeService.intent(context, action)
        return PendingIntent.getService(context, requestCode, intent, PENDING_INTENT_FLAGS)
    }
}
