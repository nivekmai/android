package io.homeassistant.companion.android.websocket

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.notifications.MessagingManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebsocketNotificationManagerTest {

    private val serverManager: ServerManager = mockk()
    private val messagingManager: MessagingManager = mockk(relaxed = true)
    private val webSocketRepository: WebSocketRepository = mockk()

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given two leases for one actual server when notification arrives then confirm and handle it exactly once`() = runTest {
        val notifications = MutableSharedFlow<Map<String, Any>>()
        val server = createServer()
        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { serverManager.webSocketRepository(server.id) } returns webSocketRepository
        coEvery { webSocketRepository.getNotifications() } returns notifications
        coEvery { webSocketRepository.ackNotification(any()) } returns true
        val manager = WebsocketNotificationManager(serverManager, messagingManager).also {
            it.collectionScope = backgroundScope
        }

        val assistLease = manager.acquire(ServerManager.SERVER_ID_ACTIVE)
        val workerLease = manager.acquire(server.id)
        assertNotNull(assistLease)
        assertNotNull(workerLease)

        notifications.emit(
            mapOf(
                "hass_confirm_id" to "confirm-1",
                "message" to MessagingManager.COMMAND_TIMER,
                "data" to mapOf(
                    MessagingManager.TIMER_SECONDS to 300,
                    MessagingManager.TIMER_SKIP_UI to false,
                    MessagingManager.PHONE_TOOL_REQUEST_ID to "request-1",
                ),
            ),
        )
        runCurrent()

        coVerify(exactly = 1) { webSocketRepository.getNotifications() }
        coVerify(exactly = 1) { webSocketRepository.ackNotification("confirm-1") }
        verify(exactly = 1) {
            messagingManager.handleMessage(
                mapOf(
                    MessagingManager.TIMER_SECONDS to "300",
                    MessagingManager.TIMER_SKIP_UI to "false",
                    MessagingManager.PHONE_TOOL_REQUEST_ID to "request-1",
                    "message" to MessagingManager.COMMAND_TIMER,
                    "webhook_id" to "webhook-7",
                ),
                "Websocket",
            )
        }

        assistLease?.close()
        runCurrent()
        notifications.emit(
            mapOf(
                "message" to MessagingManager.COMMAND_ALARM,
                "data" to mapOf(
                    MessagingManager.ALARM_HOUR to 20,
                    MessagingManager.ALARM_MINUTE to 41,
                ),
            ),
        )
        runCurrent()

        verify(exactly = 1) {
            messagingManager.handleMessage(
                match { it["message"] == MessagingManager.COMMAND_ALARM },
                "Websocket",
            )
        }

        workerLease?.close()
        runCurrent()
        notifications.emit(
            mapOf(
                "message" to "must-not-be-delivered",
            ),
        )
        runCurrent()

        verify(exactly = 0) {
            messagingManager.handleMessage(
                match { it["message"] == "must-not-be-delivered" },
                any(),
            )
        }
    }

    @Test
    fun `Given subscription setup fails when acquiring then failure is contained`() = runTest {
        val server = createServer()
        coEvery { serverManager.getServer(any<Int>()) } returns server
        coEvery { serverManager.webSocketRepository(server.id) } returns webSocketRepository
        coEvery { webSocketRepository.getNotifications() } throws IllegalStateException("connection failed")
        val manager = WebsocketNotificationManager(serverManager, messagingManager).also {
            it.collectionScope = backgroundScope
        }

        assertNull(manager.acquire(ServerManager.SERVER_ID_ACTIVE))
    }

    private fun createServer(): Server = Server(
        id = 7,
        _name = "Test Server",
        connection = ServerConnectionInfo(
            externalUrl = "https://example.com",
            webhookId = "webhook-7",
        ),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )
}
