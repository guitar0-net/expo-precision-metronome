package net.guitar0.metronome

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowService

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MetronomeServiceTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        MetronomeSession.reset()
        MetronomeSession.start(120.0, BackgroundOptions())
        settle()
    }

    @After
    fun tearDown() {
        MetronomeSession.reset()
    }

    private fun settle() = shadowOf(Looper.getMainLooper()).idle()

    private fun deliver(action: String?): ServiceController<MetronomeService> =
        Robolectric.buildService(
            MetronomeService::class.java,
            MetronomeService.intent(context, action)
        )
            .create()
            .startCommand(0, 0)

    private fun ServiceController<MetronomeService>.shadow(): ShadowService = shadowOf(get())

    /** A notification button: redelivered to the instance that is already running. */
    private fun ServiceController<MetronomeService>.tap(action: String, startId: Int) {
        get().onStartCommand(MetronomeService.intent(context, action), 0, startId)
        settle()
    }

    /** What the shade is currently showing, as opposed to what was last promoted. */
    private fun posted(): Notification? =
        shadowOf(context.getSystemService(NotificationManager::class.java))
            .getNotification(NotificationFactory.NOTIFICATION_ID)

    private val Notification.text: String?
        get() = extras.getString("android.text")

    private fun Notification.actionLabels() = actions.orEmpty().map { it.title.toString() }

    @Test
    fun start_promotes_to_the_foreground_and_keeps_running() {
        val shadow = deliver(MetronomeService.ACTION_START).shadow()

        assertEquals(
            NotificationFactory.NOTIFICATION_ID,
            shadow.lastForegroundNotificationId
        )
        assertEquals("120 BPM", shadow.lastForegroundNotification.text)
        assertEquals(Playback.Running, MetronomeSession.state.playback)
    }

    /**
     * Regression: the service used to `stopSelf()` straight away when the options were
     * already gone. Every delivery here is armed by `startForegroundService()`, so
     * returning without `startForeground()` is a ForegroundServiceDidNotStartInTimeException.
     * The window is real — the engine can fail between `start()` launching the service
     * and the intent being delivered.
     */
    @Test
    fun start_still_promotes_when_playback_ended_before_the_intent_arrived() {
        MetronomeSession.stop(null)
        settle()

        val controller = deliver(MetronomeService.ACTION_START)

        assertNotNull(
            "must promote before bailing out, or the 5 s startForeground() deadline is missed",
            controller.shadow().lastForegroundNotification
        )
        assertTrue(controller.shadow().isStoppedBySelf)
    }

    @Test
    fun the_notification_stop_button_stops_playback_with_the_notification_reason() {
        val controller = deliver(MetronomeService.ACTION_STOP)

        assertEquals(Playback.Stopped, MetronomeSession.state.playback)
        assertEquals(MetronomeService.REASON_NOTIFICATION, MetronomeSession.state.reason)
        assertTrue(controller.shadow().isStoppedBySelf)
        assertNotNull(controller.shadow().lastForegroundNotification)
    }

    @Test
    fun the_notification_pause_button_suspends_playback_without_stopping_the_service() {
        val controller = deliver(MetronomeService.ACTION_START)

        controller.tap(MetronomeService.ACTION_PAUSE, 1)

        assertEquals(Playback.Paused, MetronomeSession.state.playback)
        assertEquals(MetronomeService.REASON_NOTIFICATION, MetronomeSession.state.reason)
        // The notification is the only way back, so it — and the service behind it —
        // has to outlive the pause.
        assertFalse(controller.shadow().isStoppedBySelf)
    }

    /** End to end: an action delivered to the service flips the rendered button. */
    @Test
    fun pausing_and_resuming_swap_the_rendered_button_label() {
        val controller = deliver(MetronomeService.ACTION_START)
        settle()
        assertEquals(listOf("Pause", "Stop"), posted()?.actionLabels())

        controller.tap(MetronomeService.ACTION_PAUSE, 1)
        assertEquals(listOf("Resume", "Stop"), posted()?.actionLabels())

        controller.tap(MetronomeService.ACTION_RESUME, 2)
        assertEquals(listOf("Pause", "Stop"), posted()?.actionLabels())
    }

    /**
     * The shade is responsive enough to double-tap by accident. The session makes the
     * second tap a no-op, and nothing may be redrawn for a transition that did not
     * happen.
     */
    @Test
    fun a_repeated_pause_from_the_shade_redraws_nothing() {
        val controller = deliver(MetronomeService.ACTION_START)
        controller.tap(MetronomeService.ACTION_PAUSE, 1)
        val before = posted()

        controller.tap(MetronomeService.ACTION_PAUSE, 2)

        assertSame(before, posted())
    }

    /** Swiping the app away tears down the JS context, so no event can be delivered. */
    @Test
    fun task_removal_stops_playback_silently() {
        val controller = deliver(MetronomeService.ACTION_START)

        controller.get().onTaskRemoved(null)

        assertEquals(Playback.Stopped, MetronomeSession.state.playback)
        assertNull(MetronomeSession.state.reason)
        assertTrue(controller.shadow().isStoppedBySelf)
    }

    /** Replaces the module's refreshNotification(): the service is the only writer now. */
    @Test
    fun a_tempo_change_redraws_the_notification_in_place() {
        deliver(MetronomeService.ACTION_START)
        settle()

        MetronomeSession.setBpm(132.0)
        settle()

        assertEquals("132 BPM", posted()?.text)
    }

    /** Only what varies can go stale, and a caller-supplied text does not vary. */
    @Test
    fun a_tempo_change_under_a_caller_supplied_text_redraws_nothing() {
        MetronomeSession.start(120.0, BackgroundOptions().apply { text = "4/4" })
        settle()
        deliver(MetronomeService.ACTION_START)
        settle()
        val before = posted()

        MetronomeSession.setBpm(132.0)
        settle()

        assertSame("nothing the user can see changed, so nothing may be posted", before, posted())
    }

    @Test
    fun setting_the_same_tempo_twice_redraws_once() {
        deliver(MetronomeService.ACTION_START)
        MetronomeSession.setBpm(132.0)
        settle()
        val before = posted()

        MetronomeSession.setBpm(132.0)
        settle()

        assertSame(before, posted())
    }

    /** The service is torn down on the same transition; drawing here would race that. */
    @Test
    fun a_stop_does_not_redraw_the_notification() {
        deliver(MetronomeService.ACTION_START)
        MetronomeSession.setBpm(132.0)
        settle()
        val before = posted()

        MetronomeSession.stop("explicit")
        settle()

        assertSame(before, posted())
    }
}
