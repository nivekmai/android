package io.homeassistant.companion.android.assist.service

import android.os.SystemClock
import io.homeassistant.companion.android.common.assist.AssistPushToTalkDiagnostics

/** Bridges hardware-key callbacks from the voice interaction session to AssistActivity. */
object AssistPushToTalkController {
    private val listeners = mutableMapOf<String, () -> Unit>()
    private val pendingReleases = mutableSetOf<String>()
    private var activeSessionId: String? = null
    private var accessibilityPowerDownAt: Long? = null

    @Synchronized
    fun noteAccessibilityPowerDown() {
        accessibilityPowerDownAt = SystemClock.elapsedRealtime()
        AssistPushToTalkDiagnostics.log("controller.noteAccessibilityPowerDown")
    }

    /**
     * Consume a Power-down event only when it immediately preceded the system assistant session.
     * If Android withholds the key from accessibility, the session must retain ordinary VAD.
     */
    @Synchronized
    fun consumeRecentAccessibilityPowerDown(): Boolean {
        val observedAt = accessibilityPowerDownAt.also { accessibilityPowerDownAt = null }
        val age = observedAt?.let { SystemClock.elapsedRealtime() - it }
        val recent = age != null && age in 0..ACCESSIBILITY_POWER_DOWN_MAX_AGE_MS
        AssistPushToTalkDiagnostics.log(
            "controller.consumeAccessibilityPowerDown observed=${observedAt != null} age=$age recent=$recent",
        )
        return recent
    }

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

    private const val ACCESSIBILITY_POWER_DOWN_MAX_AGE_MS = 3_000L
}
