package net.guitar0.metronome

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    private lateinit var engine: FakePlaybackController

    @Before
    fun setUp() {
        engine = FakePlaybackController()
        MetronomeEngineHolder.attach(engine)
        MetronomeEngineHolder.bpm = 120.0
        MetronomeEngineHolder.backgroundOptions = BackgroundOptions()
    }

    @After
    fun tearDown() {
        MetronomeEngineHolder.detach(engine)
        MetronomeEngineHolder.backgroundOptions = null
        MetronomeEngineHolder.bpm = 0.0
    }

    private fun deliver(action: String?): ServiceController<MetronomeService> =
        Robolectric.buildService(
            MetronomeService::class.java,
            MetronomeService.intent(context, action)
        )
            .create()
            .startCommand(0, 0)

    private fun ServiceController<MetronomeService>.shadow(): ShadowService = shadowOf(get())

    @Test
    fun start_promotes_to_the_foreground_and_keeps_running() {
        val shadow = deliver(MetronomeService.ACTION_START).shadow()

        assertEquals(
            NotificationFactory.NOTIFICATION_ID,
            shadow.lastForegroundNotificationId
        )
        assertEquals("120 BPM", shadow.lastForegroundNotification.extras.getString("android.text"))
        assertTrue("playback must not be stopped by a plain start", engine.stopReasons.isEmpty())
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
        MetronomeEngineHolder.backgroundOptions = null

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

        assertEquals(listOf(MetronomeService.STOP_REASON_NOTIFICATION), engine.stopReasons)
        assertTrue(controller.shadow().isStoppedBySelf)
        assertNotNull(controller.shadow().lastForegroundNotification)
    }

    /** Swiping the app away tears down the JS context, so no onStop can be delivered. */
    @Test
    fun task_removal_stops_playback_silently() {
        val controller = deliver(MetronomeService.ACTION_START)

        controller.get().onTaskRemoved(null)

        assertEquals(listOf<String?>(null), engine.stopReasons)
        assertTrue(controller.shadow().isStoppedBySelf)
    }
}
