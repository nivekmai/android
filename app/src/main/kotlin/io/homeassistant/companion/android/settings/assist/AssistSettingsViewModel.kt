package io.homeassistant.companion.android.settings.assist

import android.annotation.SuppressLint
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.assist.wakeword.MicroWakeWordModelConfig
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Assist settings screen.
 */
data class AssistSettingsUiState(
    val isLoading: Boolean = true,
    val isDefaultAssistant: Boolean = false,
    val isAlarmControlEnabled: Boolean = false,
    val isTimerControlEnabled: Boolean = false,
    val isMediaControlEnabled: Boolean = false,
    val isGmailReadEnabled: Boolean = false,
    val isDriveReadEnabled: Boolean = false,
    val isCalendarWriteEnabled: Boolean = false,
    val isGmailWriteEnabled: Boolean = false,
    val isDriveWriteEnabled: Boolean = false,
    val isWakeWordEnabled: Boolean = false,
    val selectedWakeWordModel: MicroWakeWordModelConfig? = null,
    val availableModels: List<MicroWakeWordModelConfig> = emptyList(),
    val isTestingWakeWord: Boolean = false,
    val wakeWordDetected: Boolean = false,
)

@VisibleForTesting
val WAKE_WORD_TEST_DEBOUNCE = 3.seconds

@HiltViewModel
class AssistSettingsViewModel @Inject internal constructor(
    private val defaultAssistantManager: DefaultAssistantManager,
    private val assistConfigManager: AssistConfigManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistSettingsUiState())
    val uiState: StateFlow<AssistSettingsUiState> = _uiState.asStateFlow()
    private var wakeWordResetJob: Job? = null

    init {
        loadState()
    }

    @SuppressLint("MissingPermission")
    private fun loadState() {
        viewModelScope.launch {
            val models = assistConfigManager.getAvailableModels()
            var isEnabled = assistConfigManager.isWakeWordEnabled()
            val selectedModel = assistConfigManager.getSelectedWakeWordModel() ?: models.firstOrNull()
            val isDefaultAssistant = defaultAssistantManager.isDefaultAssistant()
            val isAlarmControlEnabled = assistConfigManager.isAlarmControlEnabled()
            val isTimerControlEnabled = assistConfigManager.isTimerControlEnabled()
            val isMediaControlEnabled = assistConfigManager.isMediaControlEnabled()
            val isGmailReadEnabled = assistConfigManager.isGmailReadEnabled()
            val isDriveReadEnabled = assistConfigManager.isDriveReadEnabled()
            val isCalendarWriteEnabled = assistConfigManager.isCalendarWriteEnabled()
            val isGmailWriteEnabled = assistConfigManager.isGmailWriteEnabled()
            val isDriveWriteEnabled = assistConfigManager.isDriveWriteEnabled()

            if (!isDefaultAssistant && isEnabled) {
                assistConfigManager.setWakeWordEnabled(false)
                isEnabled = false
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isDefaultAssistant = isDefaultAssistant,
                    isAlarmControlEnabled = isAlarmControlEnabled,
                    isTimerControlEnabled = isTimerControlEnabled,
                    isMediaControlEnabled = isMediaControlEnabled,
                    isGmailReadEnabled = isGmailReadEnabled,
                    isDriveReadEnabled = isDriveReadEnabled,
                    isCalendarWriteEnabled = isCalendarWriteEnabled,
                    isGmailWriteEnabled = isGmailWriteEnabled,
                    isDriveWriteEnabled = isDriveWriteEnabled,
                    isWakeWordEnabled = isEnabled,
                    selectedWakeWordModel = selectedModel,
                    availableModels = models,
                )
            }
        }
    }

    /**
     * Refresh the default assistant status.
     * Call this when returning from system settings.
     */
    fun refreshDefaultAssistantStatus() {
        loadState()
    }

    /**
     * Returns the intent to set the app as the default assistant.
     *
     * Uses RoleManager on Android 10+ with fallback to system settings.
     */
    fun getSetDefaultAssistantIntent(): Intent {
        return defaultAssistantManager.getSetDefaultAssistantIntent()
    }

    /**
     * Toggle wake word detection on or off.
     */
    @SuppressLint("MissingPermission")
    fun onToggleWakeWord(enabled: Boolean) {
        viewModelScope.launch {
            assistConfigManager.setWakeWordEnabled(enabled)
            // setWakeWordEnabled could set a model so we need to get the selected model and update the UI with it
            val model = assistConfigManager.getSelectedWakeWordModel()
            _uiState.update {
                it.copy(isWakeWordEnabled = enabled, selectedWakeWordModel = model)
            }
        }
    }

    /** Toggle whether Assist may create alarms in this phone's Clock app. */
    fun onToggleAlarmControl(enabled: Boolean) {
        _uiState.update { it.copy(isAlarmControlEnabled = enabled) }
        viewModelScope.launch {
            assistConfigManager.setAlarmControlEnabled(enabled)
        }
    }

    /** Toggle whether Assist may create timers in this phone's Clock app. */
    fun onToggleTimerControl(enabled: Boolean) {
        _uiState.update { it.copy(isTimerControlEnabled = enabled) }
        viewModelScope.launch {
            assistConfigManager.setTimerControlEnabled(enabled)
        }
    }

    /** Toggle whether Assist may start or resume media on this phone. */
    fun onToggleMediaControl(enabled: Boolean) {
        _uiState.update { it.copy(isMediaControlEnabled = enabled) }
        viewModelScope.launch {
            assistConfigManager.setMediaControlEnabled(enabled)
        }
    }

    fun onToggleGmailRead(enabled: Boolean) {
        _uiState.update { it.copy(isGmailReadEnabled = enabled) }
        viewModelScope.launch { assistConfigManager.setGmailReadEnabled(enabled) }
    }

    fun onToggleDriveRead(enabled: Boolean) {
        _uiState.update { it.copy(isDriveReadEnabled = enabled) }
        viewModelScope.launch { assistConfigManager.setDriveReadEnabled(enabled) }
    }

    fun onToggleCalendarWrite(enabled: Boolean) {
        _uiState.update { it.copy(isCalendarWriteEnabled = enabled) }
        viewModelScope.launch { assistConfigManager.setCalendarWriteEnabled(enabled) }
    }

    fun onToggleGmailWrite(enabled: Boolean) {
        _uiState.update { it.copy(isGmailWriteEnabled = enabled) }
        viewModelScope.launch { assistConfigManager.setGmailWriteEnabled(enabled) }
    }

    fun onToggleDriveWrite(enabled: Boolean) {
        _uiState.update { it.copy(isDriveWriteEnabled = enabled) }
        viewModelScope.launch { assistConfigManager.setDriveWriteEnabled(enabled) }
    }

    /**
     * Select a wake word model.
     */
    @SuppressLint("MissingPermission")
    fun onSelectWakeWordModel(model: MicroWakeWordModelConfig) {
        viewModelScope.launch {
            assistConfigManager.setSelectedWakeWordModel(model)
            _uiState.update {
                it.copy(
                    selectedWakeWordModel = model,
                    isTestingWakeWord = false,
                    wakeWordDetected = false,
                )
            }
        }
    }

    /**
     * Notify the ViewModel that a wake word was detected by the service.
     *
     * Called by the Fragment when it receives the broadcast from the service.
     * Only updates the UI when test mode is active.
     * Detection feedback resets after a debounce period.
     */
    fun onWakeWordDetected() {
        if (!_uiState.value.isTestingWakeWord) return
        wakeWordResetJob?.cancel()
        _uiState.update { it.copy(wakeWordDetected = true) }
        wakeWordResetJob = viewModelScope.launch {
            delay(WAKE_WORD_TEST_DEBOUNCE)
            _uiState.update { it.copy(wakeWordDetected = false) }
        }
    }

    /**
     * Toggle the test mode UI state.
     *
     * Called by the Fragment when the user starts or stops testing.
     * The Fragment handles BroadcastReceiver registration.
     */
    fun setTestingWakeWord(testing: Boolean) {
        wakeWordResetJob?.cancel()
        _uiState.update { it.copy(isTestingWakeWord = testing, wakeWordDetected = false) }
    }
}
