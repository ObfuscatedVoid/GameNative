package app.gamenative.service.cdn

import app.gamenative.service.contentdownloader.GlobalDownloadStats
import app.gamenative.service.cdn.AuthToken
import `in`.dragonbra.javasteam.types.ChunkData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.Closeable

/**
 * CDN Client for downloading chunks
 * Matches com.xj.standalone.steam.cdn.Client
 */
class Client : Closeable {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .build()
    
    companion object {
        private const val CHUNK_DOWNLOAD_TIMEOUT_MS = 20000L
        
        /**
         * Build URL for CDN request
         * Matches Client.Companion.a()
         */
        fun buildCDNUrl(
            server: CDNServer,
            command: String,
            query: String? = null
        ): HttpUrl {
            val scheme = if (server.protocol() == ConnectionProtocol.HTTP) "http" else "https"
            val builder = HttpUrl.Builder()
                .scheme(scheme)
                .host(server.vHost())
                .port(server.port())
                .addPathSegments(command)
            
            if (!query.isNullOrEmpty()) {
                val queryString = if (query.startsWith("?")) query.substring(1) else query
                builder.query(queryString)
            }
            
            return builder.build()
        }
    }
    
    /**
     * Download depot chunk
     * 
     * @param maxConnections Maximum concurrent connections
     * @param appId Application ID
     * @param depotId Depot ID
     * @param chunkData Chunk data to download
     * @param cdnClientPool CDN client pool for auth tokens
     * @param cdnServer CDN server to use
     * @param globalStats Global download statistics
     * @return ByteArray containing compressed chunk data
     */
    suspend fun downloadChunk(
        maxConnections: Int,
        appId: Int,
        depotId: Int,
        chunkData: ChunkData,
        cdnClientPool: CDNClientPool,
        cdnServer: CDNServer,
        globalStats: GlobalDownloadStats
    ): ByteArray = withContext(Dispatchers.IO) {
        val chunkIdHex = `in`.dragonbra.javasteam.util.Strings.toHex(chunkData.chunkID)
        
        try {
            // Acquire auth token
            val authToken = cdnServer.acquireAuthToken(cdnClientPool, depotId)
            if (authToken.result() != `in`.dragonbra.javasteam.enums.EResult.OK) {
                throw IllegalStateException("Failed to acquire CDN auth token: ${authToken.result()}")
            }
            
            // Update global stats with compressed length
            globalStats.sizeDownloaded().addAndGet(chunkData.compressedLength.toLong())
            
            // Build chunk download URL with auth token as query parameter
            // Matches Java Client.Companion.a() behavior: token is added as query parameter, not header
            val command = "depot/$depotId/chunk/${chunkIdHex}"
            val tokenString = authToken.token()
            // Java code does str.substring(1) if str starts with "?", so we pass token directly
            // buildCDNUrl will handle adding it as query parameter
            val url = buildCDNUrl(cdnServer, command, tokenString)
            
            // Create request (no Authorization header - token is in URL query parameter)
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Valve/Steam HTTP Client 1.0")
                .addHeader("Host", cdnServer.vHost())
                .addHeader("X-Requested-With", "com.valvesoftware.android.steam.community")
                .get()
                .build()
            
            // Execute request
            val startTime = System.currentTimeMillis()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Failed to download chunk $chunkIdHex: HTTP ${response.code}")
                }
                
                val body = response.body
                    ?: throw IllegalStateException("Response body is null for chunk $chunkIdHex")
                
                val chunkDataBytes = body.bytes()
                val elapsed = System.currentTimeMillis() - startTime
                
                Timber.d("Downloaded chunk $chunkIdHex (${chunkDataBytes.size} bytes) in ${elapsed}ms")
                
                return@withContext chunkDataBytes
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download chunk $chunkIdHex")
            throw IllegalStateException("Failed to download chunk $chunkIdHex: ${e.message}", e)
        }
    }
    
    override fun close() {
        httpClient.connectionPool.evictAll()
    }
}

