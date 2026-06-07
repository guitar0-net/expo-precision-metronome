package net.guitar0.metronome

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.Keep
import java.util.concurrent.atomic.AtomicBoolean

internal class MetronomeEngine(
    context: Context,
    private val onEvent: (eventName: String, payload: Map<String, Any>) -> Unit
) {
    private var nativeHandle: Long = 0
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)

    private var focusRequest: AudioFocusRequest? = null
    private var legacyFocusListener: AudioManager.OnAudioFocusChangeListener? = null

    init {
        nativeHandle = nativeCreate()
    }

    fun start(bpm: Double) {
        if (!running.compareAndSet(false, true)) return
        requestAudioFocus()
        nativeStart(nativeHandle, bpm)
    }

    fun stop(reason: String?) {
        if (!running.compareAndSet(true, false)) return
        nativeStop(nativeHandle)
        releaseAudioFocus()
        if (reason != null) {
            mainHandler.post { onEvent("onStop", mapOf("reason" to reason)) }
        }
    }

    fun setBpm(bpm: Double) {
        nativeSetBpm(nativeHandle, bpm)
    }

    fun setSound(presetIndex: Int) {
        nativeSetSound(nativeHandle, presetIndex)
    }

    fun destroy() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0
        }
    }

    // Called from JNI on the Oboe audio thread.
    @Keep
    fun onBeat(beat: Int, timestamp: Double) {
        mainHandler.post { onEvent("onBeat", mapOf("beat" to beat, "timestamp" to timestamp)) }
    }

    // Called from JNI when the Oboe stream encounters an unrecoverable error (e.g. device disconnect).
    // The C++ running_ flag is already false at this point, so nativeStop() is a no-op.
    @Keep
    fun onNativeStop(reason: String) {
        stop(reason)
    }

    private fun requestAudioFocus() {
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS ||
                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
            ) {
                stop("interruption")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(listener)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            legacyFocusListener = listener
        }
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            legacyFocusListener?.let { audioManager.abandonAudioFocus(it) }
            legacyFocusListener = null
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeStart(handle: Long, bpm: Double)
    private external fun nativeStop(handle: Long)
    private external fun nativeSetBpm(handle: Long, bpm: Double)
    private external fun nativeSetSound(handle: Long, presetIndex: Int)

    companion object {
        init {
            System.loadLibrary("metronome")
        }
    }
}
