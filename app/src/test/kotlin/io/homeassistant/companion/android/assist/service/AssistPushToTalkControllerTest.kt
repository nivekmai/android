package io.homeassistant.companion.android.assist.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistPushToTalkControllerTest {

    @Test
    fun `Given accessibility Power down when consumed once then only first session can use it`() {
        AssistPushToTalkController.noteAccessibilityPowerDown()

        assertTrue(AssistPushToTalkController.consumeRecentAccessibilityPowerDown())
        assertFalse(AssistPushToTalkController.consumeRecentAccessibilityPowerDown())
    }

    @Test
    fun `Given active session when accessibility releases before activity registers then deliver pending release`() {
        val sessionId = "accessibility-test-session"
        AssistPushToTalkController.markActive(sessionId)
        AssistPushToTalkController.releaseActive("test")

        var released = false
        AssistPushToTalkController.register(sessionId) { released = true }

        assertTrue(released)
        AssistPushToTalkController.unregister(sessionId)
    }
}
