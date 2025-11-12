package app.gamenative.service.cdn

import app.gamenative.service.content.SteamContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicInteger

/**
 * CDN Client Pool for managing CDN connections
 * Matches com.xj.standalone.steam.cdn.CDNClientPool
 */
class CDNClientPool(
    private val steamContent: SteamContent,
    private val appId: Int,
    private val scope: CoroutineScope
) {
    private val client: Client = Client()
    private var currentServer: CDNServer? = null
    private val serverList: LinkedList<CDNServer> = LinkedList()
    private val maxConnections: AtomicInteger = AtomicInteger(0)
    private val mutex: Mutex = Mutex()
    private val discoveryJob: Job

    init {
        // Start server discovery job
        discoveryJob = scope.launch {
            discoverCDNServers()
        }
    }

    /**
     * Get client from pool
     */
    fun getClient(): Client = client

    /**
     * Get current CDN server
     */
    fun getCurrentServer(): CDNServer? = currentServer

    /**
     * Get max connections
     */
    fun getMaxConnections(): Int = maxOf(maxConnections.get(), 8)

    /**
     * Close/cleanup pool
     */
    fun close() {
        discoveryJob.cancel()
        client.close()
    }

    /**
     * Select best CDN server
     */
    suspend fun selectBestCDNServer(): CDNServer {
        return mutex.withLock {
            val bestServer = selectBestAvailableServer()
            if (bestServer != null && bestServer.lastResponseTime() == 0L) {
                bestServer.setLastResponseTime(1L)
            }
            currentServer = bestServer
            bestServer ?: throw NoSuchElementException("No CDN server available")
        }
    }

    /**
     * Request CDN auth token
     * Matches CDNClientPool.q()
     */
    suspend fun requestCDNAuthToken(
        server: CDNServer,
        depotId: Int
    ): AuthToken {
        return steamContent.getCDNAuthToken(
            appId = appId,
            depotId = depotId,
            hostName = server.host(),
            parentScope = scope
        )
    }

    /**
     * Discover CDN servers
     * Matches CDNClientPool.g() and k()
     */
    private suspend fun discoverCDNServers() {
        try {
            mutex.withLock {
                val servers = steamContent.getServersForSteamPipe(
                    appId = appId,
                    parentScope = scope
                )

                // Filter and add servers
                val validServers = servers.filter { server ->
                    val allowedAppIds = server.allowedAppIds()
                    (allowedAppIds.isEmpty() || allowedAppIds.contains(appId)) &&
                    (server.type == "SteamCache" || server.type == "CDN")
                }

                // Update server list
                for (newServer in validServers) {
                    val existingServer = serverList.find { it.host() == newServer.host() }
                    if (existingServer != null) {
                        // Update existing server
                        existingServer.setWeightedLoad(newServer.weightedLoad)
                        existingServer.setScore(newServer.score)
                    } else {
                        // Add new server
                        Timber.d("Discovered new CDN server: ${newServer.host()}")
                        serverList.add(newServer)
                    }
                }

                // Update max connections based on server count
                maxConnections.set(serverList.size)

                // Select best server if none selected
                if (currentServer == null && serverList.isNotEmpty()) {
                    currentServer = selectBestAvailableServer()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to discover CDN servers")
        }
    }

    /**
     * Select best available server (not suspended)
     */
    private fun selectBestAvailableServer(): CDNServer? {
        val availableServers = serverList.filter { !it.suspended() }
        if (availableServers.isEmpty()) {
            return serverList.minByOrNull { it.lastResponseTime() }
        }
        return availableServers.minByOrNull { it.lastResponseTime() }
    }

    /**
     * Report server failure
     */
    suspend fun reportServerFailure(server: CDNServer) {
        mutex.withLock {
            server.setFailureCount(server.failureCount() + 1)
            server.setSuspendedUntil(Long.MAX_VALUE)
            server.setLastFailureTime(System.currentTimeMillis())

            if (server.failureCount() >= 3) {
                server.setSuspended(true)
                Timber.w("CDN server ${server.host()} has been suspended")
            }
        }
    }

    /**
     * Report server response time
     */
    suspend fun reportServerResponse(
        server: CDNServer,
        responseTime: Long,
        downloadTime: Long
    ) {
        mutex.withLock {
            server.setFailureCount(0)
            server.setLastResponseTime(responseTime)
        }
    }
}

