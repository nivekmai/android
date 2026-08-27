package io.homeassistant.companion.android.settings.assist

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.concurrent.futures.await
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.assist.service.AssistVoiceInteractionService
import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.ResyncRegistrationWorker.Companion.enqueueResyncRegistration
import io.homeassistant.companion.android.common.util.SuspendLazy
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val DEFAULT_WAKE_WORD = "Okay Nabu"

/**
 * Manager for Assist settings and wake word model information.
 */
interface AssistConfigManager {
    /**
     * Returns a list of all available wake word models. On unsupported devices, an empty list is returned.
     */
    suspend fun getAvailableModels(): List<MicroWakeWordModelConfig>

    /**
     * Returns whether wake word detection is enabled.
     */
    suspend fun isWakeWordEnabled(): Boolean

    /**
     * Sets whether wake word detection is enabled.
     *
     * When enabling, if no wake word model is currently selected, the first available
     * model is automatically set as the default. This also starts or stops the wake
     * word detection service accordingly.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun setWakeWordEnabled(enabled: Boolean)

    /**
     * Returns the currently selected wake word model or null if no model is selected
     * or the previously selected model is no longer available.
     */
    suspend fun getSelectedWakeWordModel(): MicroWakeWordModelConfig?

    /**
     * Sets the selected wake word model.
     *
     * If wake word detection is enabled and the selection changed, restarts the service
     * to apply the new model.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun setSelectedWakeWordModel(model: MicroWakeWordModelConfig)

    /** Returns whether Assist may create alarms in this phone's Clock app. */
    suspend fun isAlarmControlEnabled(): Boolean

    /** Enables or disables creating alarms and updates every server registration. */
    suspend fun setAlarmControlEnabled(enabled: Boolean)

    /** Returns whether Assist may create timers in this phone's Clock app. */
    suspend fun isTimerControlEnabled(): Boolean

    /** Enables or disables creating timers and updates every server registration. */
    suspend fun setTimerControlEnabled(enabled: Boolean)

    /** Returns whether Assist may start or resume media on this phone. */
    suspend fun isMediaControlEnabled(): Boolean

    /** Enables or disables media playback and updates every server registration. */
    suspend fun setMediaControlEnabled(enabled: Boolean)

    suspend fun isGmailReadEnabled(): Boolean

    suspend fun setGmailReadEnabled(enabled: Boolean)

    suspend fun isDriveReadEnabled(): Boolean

    suspend fun setDriveReadEnabled(enabled: Boolean)

    suspend fun isCalendarWriteEnabled(): Boolean

    suspend fun setCalendarWriteEnabled(enabled: Boolean)

    suspend fun isGmailWriteEnabled(): Boolean

    suspend fun setGmailWriteEnabled(enabled: Boolean)

    suspend fun isDriveWriteEnabled(): Boolean

    suspend fun setDriveWriteEnabled(enabled: Boolean)
}

class AssistConfigManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepository: PrefsRepository,
    private val serverManager: ServerManager,
    private val workManager: WorkManager,
) : AssistConfigManager {

    private val models = SuspendLazy { MicroWakeWordModelConfig.loadAvailableModels(context) }
    private val phoneControlMutex = Mutex()

    override suspend fun getAvailableModels(): List<MicroWakeWordModelConfig> {
        return models.get()
    }

    override suspend fun isWakeWordEnabled(): Boolean = prefsRepository.isWakeWordEnabled()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override suspend fun setWakeWordEnabled(enabled: Boolean) {
        prefsRepository.setWakeWordEnabled(enabled)
        if (!enabled) {
            AssistVoiceInteractionService.stopListening(context)
            return
        }

        if (getSelectedWakeWordModel() == null) {
            val available = models.get()
            val defaultModel = available.find { it.wakeWord == DEFAULT_WAKE_WORD }
                ?: available.firstOrNull()
            if (defaultModel == null) {
                FailFast.fail { "No wake word models available, not starting listener" }
                return
            }
            prefsRepository.setSelectedWakeWord(defaultModel.wakeWord)
        }
        AssistVoiceInteractionService.startListening(context)
    }

    override suspend fun getSelectedWakeWordModel(): MicroWakeWordModelConfig? {
        val wakeWordName = prefsRepository.getSelectedWakeWord() ?: return null
        return models.get().find { it.wakeWord == wakeWordName }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override suspend fun setSelectedWakeWordModel(model: MicroWakeWordModelConfig) {
        val previousWakeWord = prefsRepository.getSelectedWakeWord()
        prefsRepository.setSelectedWakeWord(model.wakeWord)

        if (model.wakeWord != previousWakeWord && prefsRepository.isWakeWordEnabled()) {
            AssistVoiceInteractionService.startListening(context)
        }
    }

    override suspend fun isAlarmControlEnabled(): Boolean = prefsRepository.isAssistAlarmControlEnabled()

    override suspend fun setAlarmControlEnabled(enabled: Boolean) {
        updatePhoneControlSettings {
            prefsRepository.setAssistAlarmControlEnabled(enabled)
        }
    }

    override suspend fun isTimerControlEnabled(): Boolean = prefsRepository.isAssistTimerControlEnabled()

    override suspend fun setTimerControlEnabled(enabled: Boolean) {
        updatePhoneControlSettings {
            prefsRepository.setAssistTimerControlEnabled(enabled)
        }
    }

    override suspend fun isMediaControlEnabled(): Boolean = prefsRepository.isAssistMediaControlEnabled()

    override suspend fun setMediaControlEnabled(enabled: Boolean) {
        updatePhoneControlSettings {
            prefsRepository.setAssistMediaControlEnabled(enabled)
        }
    }

    override suspend fun isGmailReadEnabled(): Boolean = prefsRepository.isAssistGmailReadEnabled()

    override suspend fun setGmailReadEnabled(enabled: Boolean) {
        updatePhoneControlSettings {
            prefsRepository.setAssistGmailReadEnabled(enabled)
        }
    }

    override suspend fun isDriveReadEnabled(): Boolean = prefsRepository.isAssistDriveReadEnabled()

    override suspend fun setDriveReadEnabled(enabled: Boolean) {
        updatePhoneControlSettings {
            prefsRepository.setAssistDriveReadEnabled(enabled)
        }
    }

    override suspend fun isCalendarWriteEnabled(): Boolean = prefsRepository.isAssistCalendarWriteEnabled()

    override suspend fun setCalendarWriteEnabled(enabled: Boolean) {
        updatePhoneControlSettings {
            prefsRepository.setAssistCalendarWriteEnabled(enabled)
        }
    }

    override suspend fun isGmailWriteEnabled(): Boolean = prefsRepository.isAssistGmailWriteEnabled()

    override suspend fun setGmailWriteEnabled(enabled: Boolean) {
        updatePhoneControlSettings {
            prefsRepository.setAssistGmailWriteEnabled(enabled)
        }
    }

    override suspend fun isDriveWriteEnabled(): Boolean = prefsRepository.isAssistDriveWriteEnabled()

    override suspend fun setDriveWriteEnabled(enabled: Boolean) {
        updatePhoneControlSettings {
            prefsRepository.setAssistDriveWriteEnabled(enabled)
        }
    }

    private suspend fun updatePhoneControlSettings(updatePreference: suspend () -> Unit) {
        phoneControlMutex.withLock {
            withContext(NonCancellable) {
                updatePreference()
                workManager.enqueueResyncRegistration().result.await()
            }
            serverManager.servers().forEach { server ->
                try {
                    serverManager.integrationRepository(server.id).updateRegistration(DeviceRegistration())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to update phone control registration for server ${server.id}")
                }
            }
        }
    }
}
