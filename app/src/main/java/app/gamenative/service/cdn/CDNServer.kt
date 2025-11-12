package app.gamenative.service.cdn

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * CDN Server representation
 * Matches com.xj.standalone.steam.cdn.CDNServer
 */
enum class ConnectionProtocol {
    HTTP,
    HTTPS
}

/**
 * CDN Server with connection and status information
 */
class CDNServer(
    val protocol: ConnectionProtocol,
    val host: String,
    val vHost: String,
    val port: Int,
    val type: String,
    val load: Int,
    val weightedLoad: Int,
    val numEntries: Int,
    val score: Float,
    val preferredServer: Int,
    val steamChinaOnly: Int,
    val useAsProxy: Boolean,
    val proxyRequestPathTemplate: Boolean,
    val httpsSupport: String,
    val allowedAppIds: IntArray
) {
    private val mutex: Mutex = Mutex()
    private val authTokens: ConcurrentMap<Int, AuthToken> = ConcurrentHashMap()

    // Server status tracking
    private var failureCount: Int = 0
    private var lastResponseTime: Long = 0L
    private var suspendedUntil: Long = Long.MAX_VALUE
    private var suspended: Boolean = false
    private var lastFailureTime: Long = 0L

    fun protocol(): ConnectionProtocol = protocol
    fun host(): String = host
    fun vHost(): String = vHost
    fun port(): Int = port
    fun type(): String = type
    fun load(): Int = load
    fun weightedLoad(): Int = weightedLoad
    fun numEntries(): Int = numEntries
    fun score(): Float = score
    fun preferredServer(): Int = preferredServer
    fun steamChinaOnly(): Int = steamChinaOnly
    fun useAsProxy(): Boolean = useAsProxy
    fun proxyRequestPathTemplate(): Boolean = proxyRequestPathTemplate
    fun httpsSupport(): String = httpsSupport
    fun allowedAppIds(): IntArray = allowedAppIds

    fun failureCount(): Int = failureCount
    fun lastResponseTime(): Long = lastResponseTime
    fun suspendedUntil(): Long = suspendedUntil
    fun suspended(): Boolean = suspended
    fun lastFailureTime(): Long = lastFailureTime

    fun setFailureCount(count: Int) {
        failureCount = count
    }

    fun setLastResponseTime(time: Long) {
        lastResponseTime = time
    }

    fun setSuspendedUntil(time: Long) {
        suspendedUntil = time
    }

    fun setSuspended(value: Boolean) {
        suspended = value
    }

    fun setLastFailureTime(time: Long) {
        lastFailureTime = time
    }

    private var _score: Float = score
    private var _weightedLoad: Int = weightedLoad

    fun setScore(value: Float) {
        _score = value
    }

    fun setWeightedLoad(value: Int) {
        _weightedLoad = value
    }

    /**
     * Acquire auth token for a depot
     * Matches CDNServer.a() method
     */
    suspend fun acquireAuthToken(
        cdnClientPool: CDNClientPool,
        depotId: Int
    ): AuthToken {
        return mutex.withLock {
            // Check if we have a valid cached token
            val cachedToken = authTokens[depotId]
            if (cachedToken != null && cachedToken.result() == `in`.dragonbra.javasteam.enums.EResult.OK) {
                val now = java.util.Date()
                if (cachedToken.expiration().after(now)) {
                    return@withLock cachedToken
                }
            }

            // Request new token
            val newToken = cdnClientPool.requestCDNAuthToken(this, depotId)
            
            // Only cache successful tokens - don't cache failures
            if (newToken.result() == `in`.dragonbra.javasteam.enums.EResult.OK) {
                authTokens[depotId] = newToken
            } else {
                // Remove any cached token if the new request failed
                authTokens.remove(depotId)
            }
            
            newToken
        }
    }

    override fun toString(): String {
        return "$host:$port ($type)"
    }
}

