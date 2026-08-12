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

    fun build(context: Context, options: BackgroundOptions, bpm: Double): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(options.title)
            .setContentText(options.text ?: defaultText(bpm))
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

        if (options.showStopButton) {
            builder.addAction(0, options.stopLabel, stopIntent(context))
        }

        return builder.build()
    }

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
        return PendingIntent.getActivity(context, 0, intent, PENDING_INTENT_FLAGS)
    }

    private fun stopIntent(context: Context): PendingIntent {
        val intent = MetronomeService.intent(context, MetronomeService.ACTION_STOP)
        return PendingIntent.getService(context, 1, intent, PENDING_INTENT_FLAGS)
    }
}
