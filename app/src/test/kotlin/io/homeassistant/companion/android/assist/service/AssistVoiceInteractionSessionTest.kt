package io.homeassistant.companion.android.assist.service

import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.assist.AssistActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowVoiceInteractionSession

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35])
class AssistVoiceInteractionSessionTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun `Given session when onShow then start AssistActivity and finish session`() {
        val session = AssistVoiceInteractionSession(context)
        val shadow = Shadows.shadowOf(session) as ShadowVoiceInteractionSession
        shadow.create()

        session.onPrepareShow(Bundle(), 0)
        session.onShow(Bundle(), 0)

        val startedIntent = requireNotNull(shadow.lastAssistantActivityIntent)
        assertEquals(AssistActivity::class.java.name, startedIntent.component?.className)
        assertFalse(shadow.isUiEnabled)

        // finish() is posted to the handler to avoid BadTokenException
        ShadowLooper.idleMainLooper()
        assertTrue(shadow.isFinishing)
    }

    @Test
    @Config(sdk = [25])
    fun `Given pre Android 8 session when onShow then start AssistActivity with new task flag`() {
        val session = AssistVoiceInteractionSession(context)
        val shadow = Shadows.shadowOf(session) as ShadowVoiceInteractionSession
        shadow.create()

        session.onShow(Bundle(), 0)

        val startedIntent = requireNotNull(Shadows.shadowOf(context).nextStartedActivity)
        assertEquals(AssistActivity::class.java.name, startedIntent.component?.className)
        assertTrue(startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
