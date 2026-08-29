package io.homeassistant.companion.android.assist.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.view.KeyEvent
import io.homeassistant.companion.android.assist.AssistActivity
import io.homeassistant.companion.android.common.assist.AssistPushToTalkDiagnostics
import java.util.UUID

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

        val wakeWord = args?.getString(AssistVoiceInteractionService.EXTRA_WAKE_WORD)
        val sessionId = UUID.randomUUID().toString().also { pushToTalkSessionId = it }
        val invokedByPushToTalk = showFlags and SHOW_SOURCE_PUSH_TO_TALK != 0
        AssistPushToTalkDiagnostics.log(
            "session.onShow id=${sessionId.take(8)} flags=$showFlags " +
                "decoded=${decodeShowFlags(showFlags)} pushToTalk=$invokedByPushToTalk " +
                "wakeWord=${wakeWord != null} argKeys=${args?.keySet()?.sorted() ?: emptyList<String>()}",
        )

        // Launch AssistActivity to handle the interaction
        // We use the activity because it already has all the Assist logic implemented
        val intent = AssistActivity.newInstance(
            context = context,
            wakeWordPhrase = wakeWord,
            pushToTalkSessionId = sessionId,
            pushToTalk = invokedByPushToTalk,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startVoiceActivity(intent)
            AssistPushToTalkDiagnostics.log("session.startVoiceActivity id=${sessionId.take(8)} succeeded")
        } catch (exception: RuntimeException) {
            AssistPushToTalkDiagnostics.warn("session.startVoiceActivity id=${sessionId.take(8)} failed", exception)
            throw exception
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        AssistPushToTalkDiagnostics.log("session.onKeyDown ${event.describe()}")
        if (keyCode == KeyEvent.KEYCODE_POWER) {
            event.startTracking()
            AssistPushToTalkDiagnostics.log("session tracking POWER key")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        AssistPushToTalkDiagnostics.log("session.onKeyUp ${event.describe()}")
        if (keyCode == KeyEvent.KEYCODE_POWER) {
            AssistPushToTalkDiagnostics.log("session received POWER release; forwarding release")
            pushToTalkSessionId?.let(AssistPushToTalkController::release)
            Handler(Looper.getMainLooper()).post { finish() }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onHandleAssist(state: AssistState) {
        super.onHandleAssist(state)
        AssistPushToTalkDiagnostics.log("session.onHandleAssist")
        // This provides context about the current app (screenshots, text, etc.)
    }

    override fun onHide() {
        AssistPushToTalkDiagnostics.log("session.onHide")
        super.onHide()
    }

    override fun onDestroy() {
        AssistPushToTalkDiagnostics.log("session.onDestroy")
        super.onDestroy()
    }

    private fun decodeShowFlags(flags: Int): String = buildList {
        if (flags and SHOW_WITH_ASSIST != 0) add("WITH_ASSIST")
        if (flags and SHOW_WITH_SCREENSHOT != 0) add("WITH_SCREENSHOT")
        if (flags and SHOW_SOURCE_ASSIST_GESTURE != 0) add("ASSIST_GESTURE")
        if (flags and SHOW_SOURCE_APPLICATION != 0) add("APPLICATION")
        if (flags and SHOW_SOURCE_ACTIVITY != 0) add("ACTIVITY")
        if (flags and SHOW_SOURCE_PUSH_TO_TALK != 0) add("PUSH_TO_TALK")
        if (flags and SHOW_SOURCE_NOTIFICATION != 0) add("NOTIFICATION")
        if (flags and SHOW_SOURCE_AUTOMOTIVE_SYSTEM_UI != 0) add("AUTOMOTIVE_SYSTEM_UI")
        if (flags and SHOW_WITH_ASSIST_STRUCTURE_SCREEN_CONTENT != 0) add("ASSIST_STRUCTURE_SCREEN_CONTENT")
        if (isEmpty()) add("NONE")
    }.joinToString("|")

    private fun KeyEvent.describe() =
        "keyCode=$keyCode action=$action repeat=$repeatCount flags=$flags tracking=${isTracking} canceled=${isCanceled}"
}
