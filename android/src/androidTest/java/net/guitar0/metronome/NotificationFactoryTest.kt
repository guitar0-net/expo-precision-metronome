package net.guitar0.metronome

import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationFactoryTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun default_text_reports_the_current_tempo() {
        val notification = NotificationFactory.build(context, BackgroundOptions(), 120.0)

        assertEquals("Metronome", NotificationCompat.getContentTitle(notification))
        assertEquals("120 BPM", NotificationCompat.getContentText(notification))
    }

    @Test
    fun fractional_tempo_keeps_its_decimals() {
        val notification = NotificationFactory.build(context, BackgroundOptions(), 120.5)

        assertEquals("120.5 BPM", NotificationCompat.getContentText(notification))
    }

    @Test
    fun caller_supplied_text_wins_over_the_tempo() {
        val options = BackgroundOptions().apply {
            title = "Практика"
            text = "4/4"
        }

        val notification = NotificationFactory.build(context, options, 90.0)

        assertEquals("Практика", NotificationCompat.getContentTitle(notification))
        assertEquals("4/4", NotificationCompat.getContentText(notification))
    }

    @Test
    fun stop_action_can_be_turned_off() {
        val withButton = NotificationFactory.build(context, BackgroundOptions(), 120.0)
        val withoutButton = NotificationFactory.build(
            context,
            BackgroundOptions().apply { showStopButton = false },
            120.0
        )

        assertEquals(1, withButton.actions?.size ?: 0)
        assertEquals("Stop", withButton.actions[0].title.toString())
        assertEquals(0, withoutButton.actions?.size ?: 0)
    }

    @Test
    fun notification_is_ongoing_so_the_user_cannot_swipe_playback_away_silently() {
        val notification = NotificationFactory.build(context, BackgroundOptions(), 120.0)

        assertTrue(
            "FGS notification must be ongoing",
            notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0
        )
    }
}
