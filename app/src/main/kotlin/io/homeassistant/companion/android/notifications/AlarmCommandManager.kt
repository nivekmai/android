package io.homeassistant.companion.android.notifications

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.notifications.MessagingManager.Companion.ALARM_HOUR
import io.homeassistant.companion.android.notifications.MessagingManager.Companion.ALARM_MESSAGE
import io.homeassistant.companion.android.notifications.MessagingManager.Companion.ALARM_MINUTE
import io.homeassistant.companion.android.notifications.MessagingManager.Companion.ALARM_SKIP_UI
import javax.inject.Inject

class AlarmCommandManager @Inject constructor(@ApplicationContext private val context: Context) {
    internal fun setAlarm(alarm: AlarmCommand) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, alarm.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, alarm.minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, alarm.skipUi)
            alarm.message?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

internal data class AlarmCommand(val hour: Int, val minute: Int, val message: String?, val skipUi: Boolean)

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
