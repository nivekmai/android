package io.homeassistant.companion.android.notifications

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaController.TransportControls
import android.media.session.MediaSessionManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaPlaybackCommandManagerTest {
    @Test
    fun `Given valid audiobook command when parsing then return resume request`() {
        assertEquals(
            PlayMediaCommand(PhoneMediaType.AUDIOBOOK, null),
            mapOf(MessagingManager.MEDIA_TYPE to "audiobook").toPlayMediaCommand(),
        )
    }

    @Test
    fun `Given music query when parsing then trim query`() {
        assertEquals(
            PlayMediaCommand(PhoneMediaType.MUSIC, "My Supermix"),
            mapOf(
                MessagingManager.MEDIA_TYPE to "music",
                MessagingManager.MEDIA_QUERY to "  My Supermix  ",
            ).toPlayMediaCommand(),
        )
    }

    @Test
    fun `Given invalid media type when parsing then reject command`() {
        assertNull(mapOf(MessagingManager.MEDIA_TYPE to "video").toPlayMediaCommand())
    }

    @Test
    fun `Given active Audible session when resuming audiobook then request play`() {
        val context = mockk<Context> { every { packageName } returns "io.homeassistant.test" }
        val controls = mockk<TransportControls>(relaxed = true)
        val audible = mockk<MediaController> {
            every { packageName } returns "com.audible.application"
            every { transportControls } returns controls
        }
        val sessionManager = mockk<MediaSessionManager> {
            every { getActiveSessions(any()) } returns listOf(audible)
        }
        val manager = MediaPlaybackCommandManager(
            context,
            sessionManager,
            { setOf("io.homeassistant.test") },
        )

        val result = manager.play(PlayMediaCommand(PhoneMediaType.AUDIOBOOK, null))

        assertEquals(ClockCommandResult.Success, result)
        verify(exactly = 1) { controls.play() }
    }

    @Test
    fun `Given notification access disabled when playing then fail without reading sessions`() {
        val context = mockk<Context> { every { packageName } returns "io.homeassistant.test" }
        val sessionManager = mockk<MediaSessionManager>(relaxed = true)
        val manager = MediaPlaybackCommandManager(context, sessionManager, ::emptySet)

        val result = manager.play(PlayMediaCommand(PhoneMediaType.MUSIC, null))

        assertEquals(
            ClockCommandResult.Failure("Notification access is required to control media"),
            result,
        )
        verify(exactly = 0) { sessionManager.getActiveSessions(any()) }
    }
}
