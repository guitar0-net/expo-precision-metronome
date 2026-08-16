package net.guitar0.metronome

import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NotificationFactoryTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun build(
        options: BackgroundOptions = BackgroundOptions(),
        bpm: Double = 120.0,
        playback: Playback = Playback.Running
    ) = NotificationFactory.build(context, options, bpm, playback)

    private fun android.app.Notification.actionLabels() =
        actions.orEmpty().map { it.title.toString() }

    @Test
    fun default_text_reports_the_current_tempo() {
        val notification = build()

        assertEquals("Metronome", NotificationCompat.getContentTitle(notification))
        assertEquals("120 BPM", NotificationCompat.getContentText(notification))
    }

    @Test
    fun fractional_tempo_keeps_its_decimals() {
        val notification = build(bpm = 120.5)

        assertEquals("120.5 BPM", NotificationCompat.getContentText(notification))
    }

    @Test
    fun caller_supplied_text_wins_over_the_tempo() {
        val options = BackgroundOptions().apply {
            title = "Практика"
            text = "4/4"
        }

        val notification = build(options, bpm = 90.0)

        assertEquals("Практика", NotificationCompat.getContentTitle(notification))
        assertEquals("4/4", NotificationCompat.getContentText(notification))
    }

    @Test
    fun stop_action_can_be_turned_off_but_the_pause_button_stays() {
        val withButton = build()
        val withoutButton = build(BackgroundOptions().apply { showStopButton = false })

        assertEquals(listOf("Pause", "Stop"), withButton.actionLabels())
        // Suspending playback from outside the app is the point of the notification,
        // so there is no flag that leaves it with Stop alone.
        assertEquals(listOf("Pause"), withoutButton.actionLabels())
    }

    /** The button describes what the next tap does, so the label follows the state. */
    @Test
    fun the_first_action_swaps_between_pause_and_resume() {
        assertEquals(listOf("Pause", "Stop"), build(playback = Playback.Running).actionLabels())
        assertEquals(listOf("Resume", "Stop"), build(playback = Playback.Paused).actionLabels())
    }

    @Test
    fun action_labels_are_caller_supplied() {
        val options = BackgroundOptions().apply {
            pauseLabel = "Пауза"
            resumeLabel = "Дальше"
            stopLabel = "Стоп"
        }

        assertEquals(
            listOf("Пауза", "Стоп"),
            build(options, playback = Playback.Running).actionLabels()
        )
        assertEquals(
            listOf("Дальше", "Стоп"),
            build(options, playback = Playback.Paused).actionLabels()
        )
    }

    /**
     * Two PendingIntents differing only in their action are equal to the system, so a
     * shared request code plus FLAG_UPDATE_CURRENT would make both buttons do the same
     * thing.
     */
    @Test
    fun each_action_carries_its_own_pending_intent() {
        val notification = build()

        val intents = notification.actions.map { it.actionIntent }
        assertEquals(intents.size, intents.distinct().size)
    }

    @Test
    fun notification_is_ongoing_so_the_user_cannot_swipe_playback_away_silently() {
        val notification = build()

        assertTrue(
            "FGS notification must be ongoing",
            notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0
        )
    }
}
