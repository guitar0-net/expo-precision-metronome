package net.guitar0.metronome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager

/**
 * Stops playback when the audio route changes to the built-in speaker — headphones
 * unplugged, Bluetooth disconnected. Without this the click would suddenly blast
 * out of the speaker.
 */
internal class BecomingNoisyReceiver(private val onNoisy: () -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            onNoisy()
        }
    }
}
