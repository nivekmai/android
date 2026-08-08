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
import io.homeassistant.companion.android.notifications.MessagingManager.Companion.HASS_COMMAND_ID
import io.homeassistant.companion.android.notifications.MessagingManager.Companion.PHONE_TOOL_REQUEST_ID
import io.homeassistant.companion.android.notifications.MessagingManager.Companion.TIMER_MESSAGE
import io.homeassistant.companion.android.notifications.MessagingManager.Companion.TIMER_SECONDS
import io.homeassistant.companion.android.notifications.MessagingManager.Companion.TIMER_SKIP_UI
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

private const val MIN_TIMER_DURATION_SECONDS = 1
private const val MAX_TIMER_DURATION_SECONDS = 86_400
private const val MAX_CACHED_CLOCK_COMMANDS = 100
private val CLOCK_COMMAND_CACHE_TTL = 10.minutes

@OptIn(ExperimentalTime::class)
@Singleton
class ClockCommandManager internal constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, Clock.System)

    private val cacheMutex = Mutex()
    private val cachedResults = linkedMapOf<ClockCommandRequest, CachedClockCommandResult>()

    internal suspend fun setAlarm(alarm: AlarmCommand, request: ClockCommandRequest? = null): ClockCommandResult =
        executeOnce(request) {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, alarm.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, alarm.minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, alarm.skipUi)
                alarm.message?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startClockActivity(intent)
        }

    internal suspend fun setTimer(timer: TimerCommand, request: ClockCommandRequest? = null): ClockCommandResult =
        executeOnce(request) {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, timer.duration.inWholeSeconds.toInt())
                putExtra(AlarmClock.EXTRA_SKIP_UI, timer.skipUi)
                timer.message?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startClockActivity(intent)
        }

    private suspend fun executeOnce(
        request: ClockCommandRequest?,
        command: () -> ClockCommandResult,
    ): ClockCommandResult {
        if (request == null) return command()

        return cacheMutex.withLock {
            removeExpiredResults()
            cachedResults[request]?.result ?: command().also { result ->
                cachedResults[request] = CachedClockCommandResult(clock.now(), result)
                while (cachedResults.size > MAX_CACHED_CLOCK_COMMANDS) {
                    cachedResults.remove(cachedResults.keys.first())
                }
            }
        }
    }

    private fun removeExpiredResults() {
        val now = clock.now()
        cachedResults.entries.removeAll { (_, cached) -> now - cached.createdAt >= CLOCK_COMMAND_CACHE_TTL }
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

internal data class ClockCommandRequest(val serverId: Int, val requestId: String, val protocol: ClockCommandProtocol)

internal sealed interface ClockCommandProtocol {
    data object Core : ClockCommandProtocol

    data object Legacy : ClockCommandProtocol
}

@OptIn(ExperimentalTime::class)
private data class CachedClockCommandResult(val createdAt: Instant, val result: ClockCommandResult)

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

internal fun Map<String, String>.toClockCommandRequest(serverId: Int): ClockCommandRequest? {
    get(HASS_COMMAND_ID)?.takeIf(String::isNotBlank)?.let {
        return ClockCommandRequest(serverId, it, ClockCommandProtocol.Core)
    }
    return get(PHONE_TOOL_REQUEST_ID)?.takeIf(String::isNotBlank)?.let {
        ClockCommandRequest(serverId, it, ClockCommandProtocol.Legacy)
    }
}
