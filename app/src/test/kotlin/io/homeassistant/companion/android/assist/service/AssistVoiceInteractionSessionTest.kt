package io.homeassistant.companion.android.assist.service

import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.assist.AssistActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVoiceInteractionSession

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AssistVoiceInteractionSessionTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun `Given voice activity intent then manifest exposes matching AssistActivity`() {
        val matches = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_VOICE)
                .setPackage(context.packageName),
            0,
        )

        assertTrue(matches.any { it.activityInfo.name.endsWith(".assist.AssistActivity") })
    }

    @Test
    fun `Given session when onShow then start voice-compatible AssistActivity`() {
        val session = AssistVoiceInteractionSession(context)
        val shadow = Shadows.shadowOf(session) as ShadowVoiceInteractionSession
        shadow.create()

        session.onShow(Bundle(), 0)

        val startedIntent = requireNotNull(shadow.lastVoiceActivityIntent)
        assertEquals(AssistActivity::class.java.name, startedIntent.component?.className)
        assertEquals(Intent.ACTION_MAIN, startedIntent.action)
        assertTrue(startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `Given voice activity rejected when onShow then use assistant activity`() {
        val session = AssistVoiceInteractionSession(context)
        val shadow = Shadows.shadowOf(session) as ShadowVoiceInteractionSession
        shadow.create()
        shadow.setStartVoiceActivityException(SecurityException("not allowed"))

        session.onShow(Bundle(), 0)

        val startedIntent = requireNotNull(shadow.lastAssistantActivityIntent)
        assertEquals(AssistActivity::class.java.name, startedIntent.component?.className)
    }
}
