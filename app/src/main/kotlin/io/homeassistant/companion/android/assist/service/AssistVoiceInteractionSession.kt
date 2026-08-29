package io.homeassistant.companion.android.assist.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.view.KeyEvent
import io.homeassistant.companion.android.assist.AssistActivity
import java.util.UUID
import timber.log.Timber

/**
 * Handles a single voice interaction session.
 *
 * When the user triggers the assistant (via button, gesture, or voice command),
 * a new session is created. This session launches our existing [AssistActivity]
 * to handle the actual voice interaction.
 *
 * The session provides system-level integration features like:
 * - Showing UI above other apps
 * - Receiving assist context from the current app
 * - Working from the lock screen
 */
class AssistVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    private var pushToTalkSessionId: String? = null

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Timber.d("VoiceInteractionSession onShow, flags: $showFlags")

        val wakeWord = args?.getString(AssistVoiceInteractionService.EXTRA_WAKE_WORD)
        val sessionId = UUID.randomUUID().toString().also { pushToTalkSessionId = it }
        val invokedByPushToTalk = showFlags and SHOW_SOURCE_PUSH_TO_TALK != 0

        // Launch AssistActivity to handle the interaction
        // We use the activity because it already has all the Assist logic implemented
        val intent = AssistActivity.newInstance(
            context = context,
            wakeWordPhrase = wakeWord,
            pushToTalkSessionId = sessionId,
            pushToTalk = invokedByPushToTalk,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startVoiceActivity(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_POWER) {
            event.startTracking()
            Timber.d("Tracking power key for push-to-talk session")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_POWER) {
            Timber.d("Power key released; ending push-to-talk audio")
            pushToTalkSessionId?.let(AssistPushToTalkController::release)
            Handler(Looper.getMainLooper()).post { finish() }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onHandleAssist(state: AssistState) {
        super.onHandleAssist(state)
        Timber.d("onHandleAssist called")
        // This provides context about the current app (screenshots, text, etc.)
    }
}
