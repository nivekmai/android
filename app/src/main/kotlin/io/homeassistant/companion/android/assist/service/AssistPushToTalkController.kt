package io.homeassistant.companion.android.assist.service

import io.homeassistant.companion.android.common.assist.AssistPushToTalkDiagnostics

/** Bridges hardware-key callbacks from the voice interaction session to AssistActivity. */
object AssistPushToTalkController {
    private val listeners = mutableMapOf<String, () -> Unit>()
    private val pendingReleases = mutableSetOf<String>()

    @Synchronized
    fun register(sessionId: String, listener: () -> Unit) {
        AssistPushToTalkDiagnostics.log(
            "controller.register id=${sessionId.take(8)} pending=${sessionId in pendingReleases}",
        )
        listeners[sessionId] = listener
        if (pendingReleases.remove(sessionId)) {
            AssistPushToTalkDiagnostics.log("controller delivering pending release id=${sessionId.take(8)}")
            listener()
        }
    }

    @Synchronized
    fun release(sessionId: String) {
        val listener = listeners[sessionId]
        AssistPushToTalkDiagnostics.log(
            "controller.release id=${sessionId.take(8)} listenerReady=${listener != null}",
        )
        listener?.invoke() ?: pendingReleases.add(sessionId)
    }

    @Synchronized
    fun unregister(sessionId: String) {
        AssistPushToTalkDiagnostics.log("controller.unregister id=${sessionId.take(8)}")
        listeners.remove(sessionId)
        pendingReleases.remove(sessionId)
    }
}
