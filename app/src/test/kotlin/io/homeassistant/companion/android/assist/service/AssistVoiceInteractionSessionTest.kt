package io.homeassistant.companion.android.assist.service

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowVoiceInteractionSession

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AssistVoiceInteractionSessionTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun `Given voice activity intent then manifest exposes a matching activity`() {
        val matches = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_VOICE)
                .setPackage(context.packageName),
            0,
        )

        assertTrue(matches.any { it.activityInfo.name.endsWith(".assist.AssistActivity") })
    }

    @Test
    fun `Given session when power key released then notify activity and finish session`() {
        val session = AssistVoiceInteractionSession(context)
        val shadow = Shadows.shadowOf(session) as ShadowVoiceInteractionSession
        shadow.create()

        session.onShow(Bundle(), VoiceInteractionSession.SHOW_SOURCE_PUSH_TO_TALK)

        assertFalse(shadow.isFinishing)

        val sessionId = AssistVoiceInteractionSession::class.java
            .getDeclaredField("pushToTalkSessionId")
            .apply { isAccessible = true }
            .get(session) as? String
        assertNotNull(sessionId)
        var released = false
        AssistPushToTalkController.register(sessionId!!) { released = true }

        assertTrue(session.onKeyUp(KeyEvent.KEYCODE_POWER, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_POWER)))
        ShadowLooper.idleMainLooper()
        assertTrue(released)
        assertTrue(shadow.isFinishing)
        AssistPushToTalkController.unregister(sessionId)
    }

    @Test
    fun `Given Pixel power invocation metadata then classify assist gesture as push to talk`() {
        val session = AssistVoiceInteractionSession(context)
        val shadow = Shadows.shadowOf(session) as ShadowVoiceInteractionSession
        shadow.create()

        session.onShow(
            Bundle().apply { putInt("invocation_type", 6) },
            VoiceInteractionSession.SHOW_SOURCE_ASSIST_GESTURE,
        )

        val sessionId = getPushToTalkSessionId(session)
        assertNotNull(sessionId)
        AssistPushToTalkController.unregister(sessionId!!)
    }

    @Test
    fun `Given screen gesture metadata then do not classify as push to talk`() {
        val session = AssistVoiceInteractionSession(context)
        val shadow = Shadows.shadowOf(session) as ShadowVoiceInteractionSession
        shadow.create()

        session.onShow(
            Bundle().apply { putInt("invocation_type", 1) },
            VoiceInteractionSession.SHOW_SOURCE_ASSIST_GESTURE,
        )

        assertNull(getPushToTalkSessionId(session))
    }

    private fun getPushToTalkSessionId(session: AssistVoiceInteractionSession) = AssistVoiceInteractionSession::class.java
        .getDeclaredField("pushToTalkSessionId")
        .apply { isAccessible = true }
        .get(session) as? String
}
