package io.homeassistant.companion.android.notifications

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.notifications.MessagingManager.Companion.ALARM_HOUR
import io.homeassistant.companion.android.notifications.MessagingManager.Companion.ALARM_MESSAGE
import io.homeassistant.companion.android.notifications.MessagingManager.Companion.ALARM_MINUTE
import io.homeassistant.companion.android.notifications.MessagingManager.Companion.ALARM_SKIP_UI
import io.homeassistant.companion.android.notifications.MessagingManager.Companion.TIMER_MESSAGE
import io.homeassistant.companion.android.notifications.MessagingManager.Companion.TIMER_SECONDS
import io.homeassistant.companion.android.notifications.MessagingManager.Companion.TIMER_SKIP_UI
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import timber.log.Timber

private const val MIN_TIMER_DURATION_SECONDS = 1
private const val MAX_TIMER_DURATION_SECONDS = 86_400

class ClockCommandManager @Inject constructor(@ApplicationContext private val context: Context) {
    internal fun setAlarm(alarm: AlarmCommand): ClockCommandResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, alarm.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, alarm.minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, alarm.skipUi)
            alarm.message?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startClockActivity(intent)
    }

    internal fun setTimer(timer: TimerCommand): ClockCommandResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, timer.duration.inWholeSeconds.toInt())
            putExtra(AlarmClock.EXTRA_SKIP_UI, timer.skipUi)
            timer.message?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startClockActivity(intent)
    }

    private fun startClockActivity(intent: Intent): ClockCommandResult {
        return try {
            context.startActivity(intent)
            ClockCommandResult.Success
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "No clock app can handle ${intent.action}")
            ClockCommandResult.Failure("No compatible clock app is installed")
        } catch (e: SecurityException) {
            Timber.e(e, "Clock app denied ${intent.action}")
            ClockCommandResult.Failure("The clock app denied the request")
        } catch (e: RuntimeException) {
            Timber.e(e, "Failed to launch clock app for ${intent.action}")
            ClockCommandResult.Failure("Unable to open the clock app")
        }
    }
}

internal data class AlarmCommand(val hour: Int, val minute: Int, val message: String?, val skipUi: Boolean)

internal data class TimerCommand(val duration: Duration, val message: String?, val skipUi: Boolean)

internal sealed interface ClockCommandResult {
    data object Success : ClockCommandResult

    data class Failure(val error: String) : ClockCommandResult
}

internal fun Map<String, String>.toAlarmCommand(): AlarmCommand? {
    val hour = get(ALARM_HOUR)?.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val minute = get(ALARM_MINUTE)?.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    val skipUi = get(ALARM_SKIP_UI)?.toBooleanStrictOrNull()
        ?: if (containsKey(ALARM_SKIP_UI)) return null else true
    return AlarmCommand(
        hour = hour,
        minute = minute,
        message = get(ALARM_MESSAGE)?.takeIf(String::isNotBlank),
        skipUi = skipUi,
    )
}

internal fun Map<String, String>.toTimerCommand(): TimerCommand? {
    val durationSeconds = get(TIMER_SECONDS)?.toIntOrNull()
        ?.takeIf { it in MIN_TIMER_DURATION_SECONDS..MAX_TIMER_DURATION_SECONDS }
        ?: return null
    val skipUi = get(TIMER_SKIP_UI)?.toBooleanStrictOrNull()
        ?: if (containsKey(TIMER_SKIP_UI)) return null else true
    return TimerCommand(
        duration = durationSeconds.seconds,
        message = get(TIMER_MESSAGE)?.takeIf(String::isNotBlank),
        skipUi = skipUi,
    )
}
