package io.homeassistant.companion.android.assist.service

/** Bridges hardware-key callbacks from the voice interaction session to AssistActivity. */
object AssistPushToTalkController {
    private val listeners = mutableMapOf<String, () -> Unit>()
    private val pendingReleases = mutableSetOf<String>()

    @Synchronized
    fun register(sessionId: String, listener: () -> Unit) {
        listeners[sessionId] = listener
        if (pendingReleases.remove(sessionId)) listener()
    }

    @Synchronized
    fun release(sessionId: String) {
        listeners[sessionId]?.invoke() ?: pendingReleases.add(sessionId)
    }

    @Synchronized
    fun unregister(sessionId: String) {
        listeners.remove(sessionId)
        pendingReleases.remove(sessionId)
    }
}
