package io.homeassistant.companion.android.notifications

import android.content.Intent
import android.provider.AlarmClock
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AlarmCommandManagerTest {
    @Test
    fun `Given valid alarm data when parsing then return alarm command`() {
        val command = mapOf(
            MessagingManager.ALARM_HOUR to "7",
            MessagingManager.ALARM_MINUTE to "30",
            MessagingManager.ALARM_MESSAGE to "Wake up",
            MessagingManager.ALARM_SKIP_UI to "false",
        ).toAlarmCommand()

        assertEquals(
            AlarmCommand(
                hour = 7,
                minute = 30,
                message = "Wake up",
                skipUi = false,
            ),
            command,
        )
    }

    @Test
    fun `Given alarm data without optional values when parsing then use defaults`() {
        val command = mapOf(
            MessagingManager.ALARM_HOUR to "21",
            MessagingManager.ALARM_MINUTE to "5",
        ).toAlarmCommand()

        assertEquals(
            AlarmCommand(
                hour = 21,
                minute = 5,
                message = null,
                skipUi = true,
            ),
            command,
        )
    }

    @Test
    fun `Given invalid alarm data when parsing then reject command`() {
        val invalidCommands = listOf(
            emptyMap(),
            mapOf(MessagingManager.ALARM_HOUR to "24", MessagingManager.ALARM_MINUTE to "0"),
            mapOf(MessagingManager.ALARM_HOUR to "0", MessagingManager.ALARM_MINUTE to "60"),
            mapOf(MessagingManager.ALARM_HOUR to "noon", MessagingManager.ALARM_MINUTE to "0"),
            mapOf(
                MessagingManager.ALARM_HOUR to "7",
                MessagingManager.ALARM_MINUTE to "30",
                MessagingManager.ALARM_SKIP_UI to "yes",
            ),
        )

        invalidCommands.forEach { assertNull(it.toAlarmCommand()) }
    }

    @Test
    fun `Given alarm command when setting alarm then start clock activity with parameters`() {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val manager = AlarmCommandManager(context)

        manager.setAlarm(
            AlarmCommand(
                hour = 7,
                minute = 30,
                message = "Wake up",
                skipUi = true,
            ),
        )

        val intent = shadowOf(context).nextStartedActivity
        assertEquals(AlarmClock.ACTION_SET_ALARM, intent.action)
        assertEquals(7, intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(30, intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        assertEquals("Wake up", intent.getStringExtra(AlarmClock.EXTRA_MESSAGE))
        assertTrue(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `Given alarm without message when setting alarm then omit message parameter`() {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val manager = AlarmCommandManager(context)

        manager.setAlarm(
            AlarmCommand(
                hour = 21,
                minute = 5,
                message = null,
                skipUi = false,
            ),
        )

        val intent = shadowOf(context).nextStartedActivity
        assertNull(intent.getStringExtra(AlarmClock.EXTRA_MESSAGE))
        assertFalse(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
    }
}
