package io.homeassistant.companion.android.common.data.integration.impl

import io.homeassistant.companion.android.common.assist.PersonalDataKeyManager
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationException
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.impl.IntegrationRepositoryImpl.Companion.PREF_ASK_NOTIFICATION_PERMISSION
import io.homeassistant.companion.android.common.data.integration.impl.entities.EntityResponse
import io.homeassistant.companion.android.common.data.integration.impl.entities.IntegrationRequest
import io.homeassistant.companion.android.common.data.integration.impl.entities.RegisterDeviceIntegrationRequest
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedEntityState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateDiff
import io.homeassistant.companion.android.common.data.websocket.impl.entities.StateChangedEvent
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class IntegrationRepositoryImplTest {

    private val integrationService = mockk<IntegrationService>()
    private val serverManager = mockk<ServerManager>()
    private val serverID = 42
    private val server = mockk<Server>(relaxed = true)
    private val serverConnection = mockk<ServerConnectionInfo>()
    private val connectionStateProvider = mockk<ServerConnectionStateProvider>()
    private val localStorage = mockk<LocalStorage>()
    private val prefsRepository = mockk<PrefsRepository>(relaxed = true)

    private lateinit var repository: IntegrationRepository

    @BeforeEach
    fun setUp() {
        coEvery { serverManager.getServer(serverID) } returns server
        every { server.connection } returns serverConnection
        every { server.deviceName } returns "Device name"
        coEvery { serverManager.connectionStateProvider(serverID) } returns connectionStateProvider
        coEvery { localStorage.getBooleanOrNull("${serverID}_trusted") } returns null

        val url = "http://homeassistant:8123".toHttpUrl()
        coEvery { connectionStateProvider.getApiUrls() } returns listOf(url)
        coEvery { connectionStateProvider.urlFlow(any()) } returns flowOf(UrlState.HasUrl(url.toUrl()))

        repository = IntegrationRepositoryImpl(
            integrationService,
            serverManager,
            serverID,
            localStorage,
            "",
            "",
            "",
            "",
            prefsRepository,
        )
    }

    @Test
    fun `Given an empty response when invoking renderTemplate then it returns an empty string`() = runTest {
        val expectedResult = ""
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonPrimitive(expectedResult)))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals(expectedResult, result)
    }

    @Test
    fun `Given a valid response with a number when invoking renderTemplate then it returns a valid string`() = runTest {
        val expectedResult = 42
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonPrimitive(expectedResult)))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals(expectedResult.toString(), result)
    }

    @Test
    fun `Given a valid response with a string when invoking renderTemplate then it returns a valid string`() = runTest {
        val expectedResult = "hello world"
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonPrimitive(expectedResult)))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals(expectedResult, result)
    }

    @Test
    fun `Given a valid response with a boolean when invoking renderTemplate then it returns a valid string`() = runTest {
        val expectedResult = true
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonPrimitive(expectedResult)))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals(expectedResult.toString(), result)
    }

    @Test
    fun `Given a valid response with a list when invoking renderTemplate then it returns a valid string`() = runTest {
        val expectedResult = listOf(true, false)
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonArray(expectedResult.map { JsonPrimitive(it) })))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals("[true,false]", result)
    }

    @Test
    fun `Given no preference set when checking shouldAskNotificationPermission then returns null`() = runTest {
        coEvery { localStorage.getBooleanOrNull("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION") } returns null

        val result = repository.shouldAskNotificationPermission()

        assertNull(result)
    }

    @Test
    fun `Given preference set to true when checking shouldAskNotificationPermission then returns true`() = runTest {
        coEvery { localStorage.getBooleanOrNull("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION") } returns true

        val result = repository.shouldAskNotificationPermission()

        assertTrue(result == true)
    }

    @Test
    fun `Given preference set to false when checking shouldAskNotificationPermission then returns false`() = runTest {
        coEvery { localStorage.getBooleanOrNull("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION") } returns false

        val result = repository.shouldAskNotificationPermission()

        assertEquals(false, result)
    }

    @Test
    fun `Given setAskNotificationPermission called with true then stores true for server`() = runTest {
        coEvery { localStorage.putBoolean(any(), any()) } returns Unit

        repository.setAskNotificationPermission(true)

        coVerify { localStorage.putBoolean("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION", true) }
    }

    @Test
    fun `Given setAskNotificationPermission called with false then stores false for server`() = runTest {
        coEvery { localStorage.putBoolean(any(), any()) } returns Unit

        repository.setAskNotificationPermission(false)

        coVerify { localStorage.putBoolean("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION", false) }
    }

    @Test
    fun `Given different server IDs then notification permission is stored separately per server`() = runTest {
        val otherServerId = 99
        coEvery { serverManager.getServer(otherServerId) } returns server
        val otherRepository = IntegrationRepositoryImpl(
            integrationService,
            serverManager,
            otherServerId,
            localStorage,
            "",
            "",
            "",
            "",
            prefsRepository,
        )

        coEvery { localStorage.putBoolean(any(), any()) } returns Unit

        repository.setAskNotificationPermission(true)
        otherRepository.setAskNotificationPermission(false)

        coVerify { localStorage.putBoolean("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION", true) }
        coVerify { localStorage.putBoolean("${otherServerId}_$PREF_ASK_NOTIFICATION_PERMISSION", false) }
    }

    @Nested
    inner class UpdateRegistrationTests {

        @Test
        fun `Given alarm and timer controls enabled when updating registration then advertise both commands`() = runTest {
            val requestSlot = slot<IntegrationRequest>()
            coEvery { integrationService.callWebhook(any(), capture(requestSlot)) } returns
                Response.success("content".toResponseBody())
            coEvery { localStorage.getString(any()) } returns null
            coEvery { prefsRepository.isAssistAlarmControlEnabled() } returns true
            coEvery { prefsRepository.isAssistTimerControlEnabled() } returns true
            coEvery { prefsRepository.isAssistMediaControlEnabled() } returns true

            repository.updateRegistration(DeviceRegistration())

            val request = requestSlot.captured as RegisterDeviceIntegrationRequest
            assertEquals(
                listOf("command_alarm", "command_timer", "command_play_media"),
                request.data.appData?.get("supported_device_commands"),
            )
        }

        @Test
        fun `Given phone controls disabled when updating registration then advertise an empty command list`() = runTest {
            val requestSlot = slot<IntegrationRequest>()
            coEvery { integrationService.callWebhook(any(), capture(requestSlot)) } returns
                Response.success("content".toResponseBody())
            coEvery { localStorage.getString(any()) } returns null
            coEvery { prefsRepository.isAssistAlarmControlEnabled() } returns false
            coEvery { prefsRepository.isAssistTimerControlEnabled() } returns false
            coEvery { prefsRepository.isAssistMediaControlEnabled() } returns false

            repository.updateRegistration(DeviceRegistration())

            val request = requestSlot.captured as RegisterDeviceIntegrationRequest
            assertEquals(emptyList<String>(), request.data.appData?.get("supported_device_commands"))
            assertEquals(emptyList<String>(), request.data.appData?.get("assist_personal_data_scopes"))
        }

        @Test
        fun `Given personal reads enabled when updating registration then advertise scopes and public key`() = runTest {
            val requestSlot = slot<IntegrationRequest>()
            coEvery { integrationService.callWebhook(any(), capture(requestSlot)) } returns
                Response.success("content".toResponseBody())
            coEvery { localStorage.getString(any()) } returns null
            coEvery { prefsRepository.isAssistGmailReadEnabled() } returns true
            coEvery { prefsRepository.isAssistDriveReadEnabled() } returns true
            coEvery { prefsRepository.isAssistCalendarWriteEnabled() } returns true
            mockkObject(PersonalDataKeyManager)
            coEvery { PersonalDataKeyManager.publicKeyBase64() } returns "public-key"

            try {
                repository.updateRegistration(DeviceRegistration())
            } finally {
                unmockkObject(PersonalDataKeyManager)
            }

            val appData = (requestSlot.captured as RegisterDeviceIntegrationRequest).data.appData
            assertEquals(
                listOf("gmail_readonly", "drive_readonly", "calendar_events_readwrite"),
                appData?.get("assist_personal_data_scopes"),
            )
            assertEquals("public-key", appData?.get("assist_personal_data_public_key"))
        }

        @Test
        fun `Given server is untrusted when updating registration then advertise no phone commands`() = runTest {
            val requestSlot = slot<IntegrationRequest>()
            coEvery { integrationService.callWebhook(any(), capture(requestSlot)) } returns
                Response.success("content".toResponseBody())
            coEvery { localStorage.getString(any()) } returns null
            coEvery { localStorage.getBooleanOrNull("${serverID}_trusted") } returns false
            coEvery { prefsRepository.isAssistAlarmControlEnabled() } returns true
            coEvery { prefsRepository.isAssistTimerControlEnabled() } returns true
            coEvery { prefsRepository.isAssistMediaControlEnabled() } returns true

            repository.updateRegistration(DeviceRegistration())

            val request = requestSlot.captured as RegisterDeviceIntegrationRequest
            assertEquals(emptyList<String>(), request.data.appData?.get("supported_device_commands"))
        }

        @Test
        fun `Given concurrent registration updates when preferences change then publish requests in order`() = runTest {
            val firstRequestStarted = CompletableDeferred<Unit>()
            val releaseFirstRequest = CompletableDeferred<Unit>()
            val requests = mutableListOf<RegisterDeviceIntegrationRequest>()
            var alarmEnabled = false
            coEvery { prefsRepository.isAssistAlarmControlEnabled() } coAnswers { alarmEnabled }
            coEvery { prefsRepository.isAssistTimerControlEnabled() } returns false
            coEvery { prefsRepository.isAssistMediaControlEnabled() } returns false
            coEvery { localStorage.getString(any()) } returns null
            coEvery { integrationService.callWebhook(any(), any()) } coAnswers {
                requests += secondArg<IntegrationRequest>() as RegisterDeviceIntegrationRequest
                if (requests.size == 1) {
                    firstRequestStarted.complete(Unit)
                    releaseFirstRequest.await()
                }
                Response.success("content".toResponseBody())
            }

            val firstUpdate = async { repository.updateRegistration(DeviceRegistration()) }
            firstRequestStarted.await()
            alarmEnabled = true
            val secondUpdate = async { repository.updateRegistration(DeviceRegistration()) }
            runCurrent()

            assertEquals(1, requests.size)
            releaseFirstRequest.complete(Unit)
            firstUpdate.await()
            secondUpdate.await()
            assertEquals(
                listOf(
                    emptyList<String>(),
                    listOf("command_alarm"),
                ),
                requests.map { it.data.appData?.get("supported_device_commands") },
            )
        }

        @Test
        fun `Given success when updating registration then registration is persisted`() = runTest {
            val body = "content".toResponseBody()
            coEvery { integrationService.callWebhook(any(), any()) } returns Response.success(body)

            coEvery { localStorage.getString(any()) } returns null
            coEvery { serverManager.updateServer(any()) } returns Unit

            val registration = DeviceRegistration(deviceName = "New device name")
            repository.updateRegistration(
                registration,
                allowReregistration = true,
            )

            coVerify { serverManager.updateServer(any()) }
            coVerify(exactly = 0) { integrationService.registerDevice(any(), any(), any()) }
        }

        @Test
        fun `Given success code but empty body when reregistration is allowed then new registration is tried`() = runTest {
            val body = "".toResponseBody()
            coEvery { integrationService.callWebhook(any(), any()) } returns Response.success(body)

            coEvery { localStorage.getString(any()) } returns null

            // spy to be able to mock registerDevice - we only care that it is called
            // but don't test registerDevice internals in this test
            val spyRepository = spyk(repository)
            coEvery { spyRepository.registerDevice(any()) } just Runs

            val registration = DeviceRegistration(deviceName = "New device name")
            spyRepository.updateRegistration(
                registration,
                allowReregistration = true,
            )

            coVerify { spyRepository.registerDevice(any()) }
        }

        @Test
        fun `Given known broken registration response when reregistration is not allowed then throws`() = runTest {
            val body = "".toResponseBody()
            coEvery { integrationService.callWebhook(any(), any()) } returns Response.success(body)

            coEvery { localStorage.getString(any()) } returns null
            coEvery { serverManager.updateServer(any()) } returns Unit

            // spy to be able to mock registerDevice - we only care that it is called
            // but don't test registerDevice internals in this test
            val spyRepository = spyk(repository)
            coEvery { spyRepository.registerDevice(any()) } just Runs

            val registration = DeviceRegistration(deviceName = "New device name")
            try {
                spyRepository.updateRegistration(
                    registration,
                    allowReregistration = false,
                )
                fail("Expected IntegrationException to be thrown")
            } catch (e: IntegrationException) {
                assertEquals("Device registration broken and reregistration not allowed.", e.message)
            }

            coVerify(exactly = 0) { serverManager.updateServer(any()) }
            coVerify(exactly = 0) { spyRepository.registerDevice(any()) }
        }

        @ParameterizedTest
        @ValueSource(ints = [404, 410])
        fun `Given known error code when reregistration is allowed then new registration is tried`(code: Int) = runTest {
            val body = "".toResponseBody()
            coEvery { integrationService.callWebhook(any(), any()) } returns Response.error(code, body)

            coEvery { localStorage.getString(any()) } returns null

            // spy to be able to mock registerDevice - we only care that it is called
            // but don't test registerDevice internals in this test
            val spyRepository = spyk(repository)
            coEvery { spyRepository.registerDevice(any()) } just Runs

            val registration = DeviceRegistration(deviceName = "New device name")
            spyRepository.updateRegistration(
                registration,
                allowReregistration = true,
            )

            coVerify { spyRepository.registerDevice(any()) }
        }
    }

    @Nested
    inner class EntityUpdates {
        private val webSocketRepository = mockk<WebSocketRepository>()
        private val entityId = "light.bed"

        @BeforeEach
        fun setUpWebSocket() {
            coEvery { serverManager.webSocketRepository(serverID) } returns webSocketRepository
            every { server.version } returns HomeAssistantVersion(2022, 4, 0)
            // The current states are always fetched before collecting the subscription
            coEvery { webSocketRepository.getStates() } returns emptyList()
        }

        private fun compressedState(state: String, attributes: Map<String, Any?> = emptyMap()) = CompressedEntityState(
            state = JsonPrimitive(state),
            attributes = attributes,
            lastChanged = 1_700_000_000.0,
        )

        private fun addedEvent(state: String, attributes: Map<String, Any?> = emptyMap()) = CompressedStateChangedEvent(
            added = mapOf(entityId to compressedState(state, attributes)),
        )

        private fun changedEvent(state: String) = CompressedStateChangedEvent(
            changed = mapOf(entityId to CompressedStateDiff(plus = CompressedEntityState(state = JsonPrimitive(state)))),
        )

        private fun givenCurrentStatesFetchReturns(state: String) {
            coEvery { webSocketRepository.getStates() } returns listOf(
                EntityResponse(
                    entityId = entityId,
                    state = state,
                    attributes = emptyMap(),
                    lastChanged = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
                    lastUpdated = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
                ),
            )
        }

        @Test
        fun `Given compressed state changes when collecting entity updates then added entities and resolved diffs are emitted`() = runTest {
            coEvery { webSocketRepository.getCompressedStateAndChanges() } returns flowOf(
                addedEvent("on", attributes = mapOf("brightness" to 100)),
                changedEvent("off"),
            )

            val updates = checkNotNull(repository.getEntityUpdates()).toList()

            assertEquals(listOf("on", "off"), updates.map { it.state })
            // The diff keeps the attributes of the state it applies to
            assertEquals(mapOf<String, Any?>("brightness" to 100), updates[1].attributes)
            coVerify(exactly = 1) { webSocketRepository.getStates() }
        }

        @Test
        fun `Given a collector joining after the initial states when collecting entity updates then the seeded states resolve the diff`() = runTest {
            coEvery { webSocketRepository.getCompressedStateAndChanges() } returns flowOf(changedEvent("off"))
            givenCurrentStatesFetchReturns("off")

            val updates = checkNotNull(repository.getEntityUpdates()).toList()

            assertEquals("off", updates.single().state)
        }

        @Test
        fun `Given a diff for an unknown entity and a failing states fetch when collecting entity updates then the diff is skipped`() = runTest {
            coEvery { webSocketRepository.getCompressedStateAndChanges() } returns flowOf(changedEvent("off"))
            coEvery { webSocketRepository.getStates() } returns null

            val updates = checkNotNull(repository.getEntityUpdates()).toList()

            assertTrue(updates.isEmpty())
        }

        @Test
        fun `Given a diff for a removed entity when collecting entity updates then the diff is dropped`() = runTest {
            coEvery { webSocketRepository.getCompressedStateAndChanges() } returns flowOf(
                addedEvent("on"),
                CompressedStateChangedEvent(removed = listOf(entityId)),
                changedEvent("off"),
            )

            val updates = checkNotNull(repository.getEntityUpdates()).toList()

            assertEquals(listOf("on"), updates.map { it.state })
            coVerify(exactly = 1) { webSocketRepository.getStates() }
        }

        @Test
        fun `Given a states fetch when collecting entity updates for ids then only the subscribed entities are kept`() = runTest {
            coEvery { webSocketRepository.getCompressedStateAndChanges(listOf(entityId)) } returns flowOf(
                changedEvent("off"),
            )
            coEvery { webSocketRepository.getStates() } returns listOf(
                EntityResponse(
                    entityId = entityId,
                    state = "off",
                    attributes = emptyMap(),
                    lastChanged = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
                    lastUpdated = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
                ),
                EntityResponse(
                    entityId = "light.other",
                    state = "on",
                    attributes = emptyMap(),
                    lastChanged = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
                    lastUpdated = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
                ),
            )

            val updates = checkNotNull(repository.getEntityUpdates(listOf(entityId))).toList()

            assertEquals(listOf(entityId), updates.map { it.entityId })
        }

        @Test
        fun `Given compressed state changes when collecting entity updates for ids then the subscription filters without admin rights`() = runTest {
            every { server.user.isAdmin } returns false
            coEvery { webSocketRepository.getCompressedStateAndChanges(listOf(entityId)) } returns flowOf(addedEvent("on"))

            val updates = checkNotNull(repository.getEntityUpdates(listOf(entityId))).toList()

            assertEquals("on", updates.single().state)
        }

        @Test
        fun `Given an older server when collecting entity updates then state changed events are used`() = runTest {
            every { server.version } returns HomeAssistantVersion(2022, 3, 0)
            val entity = Entity(
                entityId = entityId,
                state = "on",
                attributes = emptyMap(),
                lastChanged = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
                lastUpdated = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
            )
            coEvery { webSocketRepository.getStateChanges() } returns flowOf(StateChangedEvent(entityId, newState = entity))

            val updates = checkNotNull(repository.getEntityUpdates()).toList()

            assertEquals(entity, updates.single())
            coVerify(exactly = 0) { webSocketRepository.getCompressedStateAndChanges() }
        }
    }
}
