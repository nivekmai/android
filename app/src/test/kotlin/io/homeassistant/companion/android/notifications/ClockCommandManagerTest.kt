package io.homeassistant.companion.android.notifications

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.testing.unit.FakeClock
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.test.runTest
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
@OptIn(ExperimentalTime::class)
class ClockCommandManagerTest {
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
    fun `Given alarm command when setting alarm then start clock activity with parameters`() = runTest {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val manager = ClockCommandManager(context)

        val result = manager.setAlarm(
            AlarmCommand(
                hour = 7,
                minute = 30,
                message = "Wake up",
                skipUi = true,
            ),
        )

        assertEquals(ClockCommandResult.Success, result)
        val intent = shadowOf(context).nextStartedActivity
        assertEquals(AlarmClock.ACTION_SET_ALARM, intent.action)
        assertEquals(7, intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(30, intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        assertEquals("Wake up", intent.getStringExtra(AlarmClock.EXTRA_MESSAGE))
        assertTrue(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `Given alarm without message when setting alarm then omit message parameter`() = runTest {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val manager = ClockCommandManager(context)

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

    @Test
    fun `Given valid timer data when parsing then return timer command`() {
        val command = mapOf(
            MessagingManager.TIMER_SECONDS to "300",
            MessagingManager.TIMER_MESSAGE to "Tea",
            MessagingManager.TIMER_SKIP_UI to "false",
        ).toTimerCommand()

        assertEquals(
            TimerCommand(
                duration = 300.seconds,
                message = "Tea",
                skipUi = false,
            ),
            command,
        )
    }

    @Test
    fun `Given timer data without optional values when parsing then use defaults`() {
        val command = mapOf(
            MessagingManager.TIMER_SECONDS to "1",
        ).toTimerCommand()

        assertEquals(
            TimerCommand(
                duration = 1.seconds,
                message = null,
                skipUi = true,
            ),
            command,
        )
    }

    @Test
    fun `Given invalid timer data when parsing then reject command`() {
        val invalidCommands = listOf(
            emptyMap(),
            mapOf(MessagingManager.TIMER_SECONDS to "0"),
            mapOf(MessagingManager.TIMER_SECONDS to "86401"),
            mapOf(MessagingManager.TIMER_SECONDS to "five"),
            mapOf(
                MessagingManager.TIMER_SECONDS to "300",
                MessagingManager.TIMER_SKIP_UI to "yes",
            ),
        )

        invalidCommands.forEach { assertNull(it.toTimerCommand()) }
    }

    @Test
    fun `Given timer command when setting timer then start clock activity with parameters`() = runTest {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val manager = ClockCommandManager(context)

        val result = manager.setTimer(
            TimerCommand(
                duration = 300.seconds,
                message = "Tea",
                skipUi = true,
            ),
        )

        assertEquals(ClockCommandResult.Success, result)
        val intent = shadowOf(context).nextStartedActivity
        assertEquals(AlarmClock.ACTION_SET_TIMER, intent.action)
        assertEquals(300, intent.getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
        assertEquals("Tea", intent.getStringExtra(AlarmClock.EXTRA_MESSAGE))
        assertTrue(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `Given timer without message when setting timer then omit message parameter`() = runTest {
        val context = ApplicationProvider.getApplicationContext<HiltTestApplication>()
        val manager = ClockCommandManager(context)

        manager.setTimer(
            TimerCommand(
                duration = 86_400.seconds,
                message = null,
                skipUi = false,
            ),
        )

        val intent = shadowOf(context).nextStartedActivity
        assertNull(intent.getStringExtra(AlarmClock.EXTRA_MESSAGE))
        assertFalse(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
    }

    @Test
    fun `Given no compatible clock app when setting timer then return failure`() = runTest {
        val context = mockk<Context>()
        every { context.startActivity(any()) } throws ActivityNotFoundException()
        val manager = ClockCommandManager(context)

        val result = manager.setTimer(
            TimerCommand(
                duration = 300.seconds,
                message = null,
                skipUi = true,
            ),
        )

        assertEquals(ClockCommandResult.Failure("No compatible clock app is installed"), result)
    }

    @Test
    fun `Given duplicate request when setting alarm then launch once and return cached result`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val manager = ClockCommandManager(context, FakeClock())
        val alarm = AlarmCommand(hour = 7, minute = 30, message = null, skipUi = true)
        val request = ClockCommandRequest(1, "request-1", ClockCommandProtocol.Core)

        val firstResult = manager.setAlarm(alarm, request)
        val duplicateResult = manager.setAlarm(alarm, request)

        assertEquals(ClockCommandResult.Success, firstResult)
        assertEquals(firstResult, duplicateResult)
        verify(exactly = 1) { context.startActivity(any()) }
    }

    @Test
    fun `Given cached request expired when setting alarm then launch again`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val clock = FakeClock()
        val manager = ClockCommandManager(context, clock)
        val alarm = AlarmCommand(hour = 7, minute = 30, message = null, skipUi = true)
        val request = ClockCommandRequest(1, "request-1", ClockCommandProtocol.Core)

        manager.setAlarm(alarm, request)
        clock.currentInstant += 11.minutes
        manager.setAlarm(alarm, request)

        verify(exactly = 2) { context.startActivity(any()) }
    }

    @Test
    fun `Given cache reaches its bound when oldest request repeats then launch it again`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val manager = ClockCommandManager(context, FakeClock())
        val timer = TimerCommand(duration = 1.seconds, message = null, skipUi = true)

        repeat(101) { index ->
            manager.setTimer(timer, ClockCommandRequest(1, "request-$index", ClockCommandProtocol.Core))
        }
        manager.setTimer(timer, ClockCommandRequest(1, "request-0", ClockCommandProtocol.Core))

        verify(exactly = 102) { context.startActivity(any()) }
    }

    @Test
    fun `Given both request identifiers when parsing then prefer Core protocol`() {
        val request = mapOf(
            MessagingManager.HASS_COMMAND_ID to "core-request",
            MessagingManager.PHONE_TOOL_REQUEST_ID to "legacy-request",
        ).toClockCommandRequest(serverId = 7)

        assertEquals(ClockCommandRequest(7, "core-request", ClockCommandProtocol.Core), request)
    }

    @Test
    fun `Given legacy request identifier when parsing then use legacy protocol`() {
        val request = mapOf(
            MessagingManager.PHONE_TOOL_REQUEST_ID to "legacy-request",
        ).toClockCommandRequest(serverId = 7)

        assertEquals(ClockCommandRequest(7, "legacy-request", ClockCommandProtocol.Legacy), request)
    }
}
