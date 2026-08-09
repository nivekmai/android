package io.homeassistant.companion.android.notifications

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.sensors.NotificationSensorManager
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

private const val AUDIBLE_PACKAGE = "com.audible.application"
private val AUDIOBOOK_PACKAGES = listOf(AUDIBLE_PACKAGE)
private val MUSIC_PACKAGES = listOf(
    "com.google.android.apps.youtube.music",
    "com.spotify.music",
    "com.amazon.mp3",
    "com.apple.android.music",
    "com.pandora.android",
    "com.aspiro.tidal",
    "deezer.android.app",
)

internal enum class PhoneMediaType(val value: String) {
    AUDIOBOOK("audiobook"),
    MUSIC("music"),
    ;

    companion object {
        fun fromValue(value: String?): PhoneMediaType? = entries.firstOrNull { it.value == value }
    }
}

internal data class PlayMediaCommand(val type: PhoneMediaType, val query: String?)

@Singleton
class MediaPlaybackCommandManager internal constructor(
    @ApplicationContext private val context: Context,
    private val mediaSessionManager: MediaSessionManager,
    private val enabledListenerPackages: () -> Set<String>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context,
        context.getSystemService()!!,
        { NotificationManagerCompat.getEnabledListenerPackages(context) },
    )

    internal fun play(command: PlayMediaCommand): ClockCommandResult {
        if (context.packageName !in enabledListenerPackages()) {
            return ClockCommandResult.Failure("Notification access is required to control media")
        }

        return try {
            val packages = packagesFor(command.type)
            val sessions = mediaSessionManager.getActiveSessions(
                ComponentName(context, NotificationSensorManager::class.java),
            )
            selectController(sessions, packages)?.let { controller ->
                if (command.query != null || command.type == PhoneMediaType.MUSIC) {
                    controller.transportControls.playFromSearch(command.query.orEmpty(), null)
                } else {
                    controller.transportControls.play()
                }
                return ClockCommandResult.Success
            }

            val packageName = recentMediaPackage(packages) ?: packages.firstOrNull(::isInstalled)
                ?: return ClockCommandResult.Failure("No compatible media app is installed")
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                setPackage(packageName)
                putExtra(SearchManager.QUERY, command.query.orEmpty())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) == null) {
                return ClockCommandResult.Failure("The selected media app cannot start playback")
            }
            context.startActivity(intent)
            ClockCommandResult.Success
        } catch (e: SecurityException) {
            Timber.e(e, "Media session access was denied")
            ClockCommandResult.Failure("Notification access is required to control media")
        } catch (e: RuntimeException) {
            Timber.e(e, "Unable to start phone media playback")
            ClockCommandResult.Failure("Unable to start media playback")
        }
    }

    private fun selectController(sessions: List<MediaController>, packages: List<String>): MediaController? {
        recentMediaPackage(packages)?.let { recent ->
            sessions.firstOrNull { it.packageName == recent }?.let { return it }
        }
        return packages.firstNotNullOfOrNull { packageName ->
            sessions.firstOrNull { it.packageName == packageName }
        }
    }

    private fun recentMediaPackage(packages: List<String>): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return mediaSessionManager.mediaKeyEventSessionPackageName.takeIf { it in packages }
    }

    private fun isInstalled(packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName) != null

    private fun packagesFor(type: PhoneMediaType): List<String> = when (type) {
        PhoneMediaType.AUDIOBOOK -> AUDIOBOOK_PACKAGES
        PhoneMediaType.MUSIC -> MUSIC_PACKAGES
    }
}

internal fun Map<String, String>.toPlayMediaCommand(): PlayMediaCommand? {
    val type = PhoneMediaType.fromValue(get(MessagingManager.MEDIA_TYPE)) ?: return null
    val query = get(MessagingManager.MEDIA_QUERY)?.trim()?.takeIf(String::isNotEmpty)
    return PlayMediaCommand(type, query)
}
