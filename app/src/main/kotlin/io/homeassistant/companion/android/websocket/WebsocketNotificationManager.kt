package io.homeassistant.companion.android.websocket

import androidx.annotation.VisibleForTesting
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.notifications.MessagingManager
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Owns the app's per-server local-push subscriptions.
 *
 * Both the persistent WebSocket worker and a foreground Assist session can need the same
 * subscription. Leases keep one collector alive per actual server so a notification is confirmed
 * and delivered to [MessagingManager] exactly once.
 */
@Singleton
class WebsocketNotificationManager @Inject constructor(
    private val serverManager: ServerManager,
    private val messagingManager: MessagingManager,
) {
    companion object {
        private const val SOURCE = "Websocket"
        private val ACTION_EXTRA_KEYS = listOf("uri", "behavior", "authenticationRequired")
        private val RETRY_DELAY = 1.seconds
    }

    @VisibleForTesting
    internal var collectionScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val collectorsMutex = Mutex()
    private val collectors = mutableMapOf<Int, ActiveCollector>()

    /**
     * Acquire a local-push subscription for [serverId].
     *
     * The first acquire does not return until Home Assistant acknowledges
     * `mobile_app/push_notification_channel` and the local collector has started.
     */
    suspend fun acquire(serverId: Int): Lease? = try {
        val actualServerId = serverManager.getServer(serverId)?.id ?: return null

        collectorsMutex.withLock {
            collectors[actualServerId]?.takeIf { it.job.isActive }?.let { existing ->
                existing.leaseCount++
                return@withLock Lease(this, actualServerId)
            }
            collectors.remove(actualServerId)?.job?.cancel()

            val notifications =
                serverManager.webSocketRepository(actualServerId).getNotifications() ?: return@withLock null
            val job = collectionScope.launch(start = CoroutineStart.UNDISPATCHED) {
                collectNotifications(actualServerId, notifications)
            }
            collectors[actualServerId] = ActiveCollector(job = job, leaseCount = 1)
            Lease(this, actualServerId)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Unable to start WebSocket notifications for server $serverId")
        null
    }

    private suspend fun collectNotifications(serverId: Int, initialNotifications: Flow<Map<String, Any>>) {
        var notifications: Flow<Map<String, Any>>? = initialNotifications
        while (currentCoroutineContext().isActive) {
            try {
                notifications?.collect { notification ->
                    handleNotification(serverId, notification)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unable to collect WebSocket notifications for server $serverId")
            }

            // A local-push flow normally reconnects itself and never completes. If it does end,
            // keep the active leases useful by establishing a fresh acknowledged subscription.
            delay(RETRY_DELAY)
            notifications = try {
                serverManager.webSocketRepository(serverId).getNotifications()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unable to restart WebSocket notifications for server $serverId")
                null
            }
        }
    }

    private suspend fun handleNotification(serverId: Int, notification: Map<String, Any>) {
        notification["hass_confirm_id"]?.let { confirmId ->
            try {
                serverManager.webSocketRepository(serverId).ackNotification(confirmId.toString())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unable to confirm received notification")
            }
        }

        val flattened = mutableMapOf<String, String>()
        (notification["data"] as? Map<*, *>)?.forEach { (key, value) ->
            if (key == "actions" && value is List<*>) {
                value.forEachIndexed { index, action ->
                    if (action is Map<*, *>) {
                        flattened["action_${index + 1}_key"] = action["action"].toString()
                        flattened["action_${index + 1}_title"] = action["title"].toString()
                        ACTION_EXTRA_KEYS.forEach { extraKey ->
                            action[extraKey]?.let { extraValue ->
                                flattened["action_${index + 1}_$extraKey"] = extraValue.toString()
                            }
                        }
                    }
                }
            } else {
                flattened[key.toString()] = value.toString()
            }
        }

        // Message and title are in the root unlike all the other notification fields.
        listOf("message", "title").forEach { key ->
            notification[key]?.let { flattened[key] = it.toString() }
        }
        serverManager.getServer(serverId)?.let { server ->
            flattened["webhook_id"] = server.connection.webhookId.toString()
        }
        messagingManager.handleMessage(flattened, SOURCE)
    }

    private fun release(serverId: Int) {
        collectionScope.launch(start = CoroutineStart.UNDISPATCHED) {
            collectorsMutex.withLock {
                val collector = collectors[serverId] ?: return@withLock
                collector.leaseCount--
                if (collector.leaseCount <= 0) {
                    collectors.remove(serverId)
                    collector.job.cancel()
                }
            }
        }
    }

    class Lease internal constructor(private val manager: WebsocketNotificationManager, private val serverId: Int) :
        AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                manager.release(serverId)
            }
        }
    }

    private data class ActiveCollector(val job: Job, var leaseCount: Int)
}
