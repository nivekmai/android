package io.homeassistant.companion.android.assist.service

import io.homeassistant.companion.android.common.assist.AssistPushToTalkDiagnostics

/** Bridges hardware-key callbacks from the voice interaction session to AssistActivity. */
object AssistPushToTalkController {
    private val listeners = mutableMapOf<String, () -> Unit>()
    private val pendingReleases = mutableSetOf<String>()
    private var activeSessionId: String? = null

    @Synchronized
    fun markActive(sessionId: String) {
        activeSessionId = sessionId
        AssistPushToTalkDiagnostics.log("controller.markActive id=${sessionId.take(8)}")
    }

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
    fun releaseActive(source: String) {
        val sessionId = activeSessionId
        AssistPushToTalkDiagnostics.log(
            "controller.releaseActive source=$source id=${sessionId?.take(8)}",
        )
        sessionId?.let(::release)
    }

    @Synchronized
    fun unregister(sessionId: String) {
        AssistPushToTalkDiagnostics.log("controller.unregister id=${sessionId.take(8)}")
        listeners.remove(sessionId)
        pendingReleases.remove(sessionId)
        if (activeSessionId == sessionId) activeSessionId = null
    }
}
