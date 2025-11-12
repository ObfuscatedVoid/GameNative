package app.gamenative.service.content

import app.gamenative.service.cdn.AuthToken
import app.gamenative.service.cdn.CDNServer
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesContentsystemSteamclient
import `in`.dragonbra.javasteam.rpc.service.ContentServerDirectory
import `in`.dragonbra.javasteam.steam.handlers.steamcontent.SteamContent as JavaSteamContent
import `in`.dragonbra.javasteam.steam.cdn.AuthToken as JavaAuthToken
import `in`.dragonbra.javasteam.steam.cdn.Server as JavaServer
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.callback.ServiceMethodResponse
import `in`.dragonbra.javasteam.types.AsyncJobSingle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import timber.log.Timber
import java.util.Date

/**
 * Adapter for JavaSteam's SteamContent to work with our CDN classes
 */
class SteamContent(
    private val javaSteamContent: JavaSteamContent
) {
    /**
     * Get CDN auth token
     * Wraps JavaSteamContent.getCDNAuthToken()
     */
    suspend fun getCDNAuthToken(
        appId: Int,
        depotId: Int,
        hostName: String,
        parentScope: CoroutineScope
    ): AuthToken {
        val deferred = javaSteamContent.getCDNAuthToken(appId, depotId, hostName, parentScope)
        val javaToken = deferred.await()
        return AuthToken(
            result = javaToken.result,
            token = javaToken.token,
            expiration = javaToken.expiration
        )
    }

    /**
     * Get CDN auth token (GameNative implementation)
     * Implements the same logic as the decompiled com.xj.standalone.steam.content.SteamContent.getCDNAuthToken()
     * Instead of wrapping javaSteamContent.getCDNAuthToken(), directly calls ContentServerDirectory service
     */
    suspend fun getCDNAuthTokenGN(
        appId: Int,
        depotId: Int,
        hostName: String,
        parentScope: CoroutineScope
    ): AuthToken {
        // Create the request
        val request = SteammessagesContentsystemSteamclient.CContentServerDirectory_GetCDNAuthToken_Request.newBuilder()
            .setAppId(appId)
            .setDepotId(depotId)
            .setHostName(hostName)
            .build()

        // Get ContentServerDirectory service via SteamUnifiedMessages
        // Access protected 'client' field from ClientMsgHandler using reflection
        val superclass = javaSteamContent.javaClass.superclass
            ?: throw IllegalStateException("JavaSteamContent has no superclass")
        val clientField = try {
            superclass.getDeclaredField("client")
        } catch (e: NoSuchFieldException) {
            throw IllegalStateException("Unable to find 'client' field in ClientMsgHandler", e)
        }
        clientField.isAccessible = true
        val steamClient = clientField.get(javaSteamContent) as? `in`.dragonbra.javasteam.steam.steamclient.SteamClient
            ?: throw NullPointerException("Unable to get SteamClient from JavaSteamContent (client field is null)")

        // Check authentication status - SteamClient should automatically include session auth in RPC calls,
        // but we verify the session exists to provide better error messages
        val steamID = steamClient.steamID
        val isAuthenticated = steamID?.isValid == true
        Timber.d("Requesting CDN auth token for appId=$appId, depotId=$depotId, hostName=$hostName")
        Timber.d("SteamClient authentication status: isAuthenticated=$isAuthenticated, steamID=${steamID?.convertToUInt64()}")
        
        if (!isAuthenticated) {
            Timber.w("SteamClient is not authenticated! CDN auth token request may fail. steamID=$steamID")
            // Note: We still proceed with the request because:
            // 1. JavaSteam's SteamUnifiedMessages should handle auth automatically if session exists
            // 2. The request will fail with a clear error if auth is truly missing
            // 3. This allows us to see what error Steam actually returns
        }

        val steamUnifiedMessages = steamClient.getHandler(SteamUnifiedMessages::class.java)
            ?: throw NullPointerException("Unable to get SteamUnifiedMessages handler")
        val contentServerDirectory = steamUnifiedMessages.createService(ContentServerDirectory::class.java)
            ?: throw NullPointerException("ContentServerDirectoryService is null")

        // Call getCDNAuthToken and await the response
        // Note: JavaSteam's SteamUnifiedMessages automatically includes the SteamClient's session authentication
        // in the RPC request headers. The session token is managed internally by SteamClient after logon.
        Timber.d("Calling ContentServerDirectory.getCDNAuthToken() via SteamUnifiedMessages")
        val asyncJob: AsyncJobSingle<ServiceMethodResponse<SteammessagesContentsystemSteamclient.CContentServerDirectory_GetCDNAuthToken_Response.Builder>> =
            contentServerDirectory.getCDNAuthToken(request)
        val serviceMethodResponse = asyncJob.await()

        Timber.d("CDN auth token response received: result=${serviceMethodResponse.result}")

        // Convert ServiceMethodResponse to AuthToken
        val response = (serviceMethodResponse.body as SteammessagesContentsystemSteamclient.CContentServerDirectory_GetCDNAuthToken_Response.Builder)
            .build()

        // Handle expiration time - if 0 or invalid, use a fallback
        val expirationTime = response.expirationTime
        val expirationDate = if (expirationTime > 0) {
            Date((expirationTime * 1000).toLong())
        } else {
            // If expirationTime is 0 or invalid, use current time + 1 hour as fallback
            // This prevents the 1970 date issue
            Timber.w("CDN auth token has invalid expirationTime (0 or negative): $expirationTime. Using current time + 1 hour as fallback.")
            Date(System.currentTimeMillis() + 3600000)
        }

        if (serviceMethodResponse.result != EResult.OK) {
            Timber.w("CDN auth token request failed with result: ${serviceMethodResponse.result} for appId=$appId, depotId=$depotId, hostName=$hostName")
            Timber.w("Response details - token length: ${response.token.length}, expirationTime: ${response.expirationTime}, token empty: ${response.token.isEmpty()}")
            // Possible reasons for failure:
            // - User doesn't own the app/depot
            // - Invalid depot/app combination  
            // - Invalid hostName for this depot
            // - Rate limiting
            // - Authentication/session issues (though steamID shows as valid)
        } else {
            val tokenPreview = if (response.token.isNotEmpty()) {
                "${response.token.take(10)}...${response.token.takeLast(5)}"
            } else {
                "EMPTY"
            }
            Timber.d("CDN auth token acquired successfully. Token preview: $tokenPreview, expires at: $expirationDate")
        }

        return AuthToken(
            result = serviceMethodResponse.result,
            token = response.token,
            expiration = expirationDate
        )
    }

    /**
     * Get servers for SteamPipe
     * Wraps JavaSteamContent.getServersForSteamPipe()
     */
    suspend fun getServersForSteamPipe(
        appId: Int,
        parentScope: CoroutineScope
    ): List<CDNServer> {
        val deferred = javaSteamContent.getServersForSteamPipe(
            appId,
            null, // cellId
            parentScope
        )
        val javaServers = deferred.await()
        return javaServers.map { javaServer ->
            convertJavaServerToCDNServer(javaServer)
        }
    }

    /**
     * Convert JavaSteam Server to our CDNServer
     */
    private fun convertJavaServerToCDNServer(javaServer: JavaServer): CDNServer {
        val protocol = when (javaServer.protocol) {
            `in`.dragonbra.javasteam.steam.cdn.Server.ConnectionProtocol.HTTP ->
                app.gamenative.service.cdn.ConnectionProtocol.HTTP
            `in`.dragonbra.javasteam.steam.cdn.Server.ConnectionProtocol.HTTPS ->
                app.gamenative.service.cdn.ConnectionProtocol.HTTPS
        }

        return CDNServer(
            protocol = protocol,
            host = javaServer.host,
            vHost = javaServer.vHost,
            port = javaServer.port,
            type = javaServer.type ?: "",
            load = javaServer.load,
            weightedLoad = javaServer.weightedLoad.toInt(),
            numEntries = javaServer.numEntries,
            score = 0.0f, // Not available in JavaServer
            preferredServer = 0, // Not available in JavaServer
            steamChinaOnly = if (javaServer.steamChinaOnly) 1 else 0,
            useAsProxy = javaServer.useAsProxy,
            proxyRequestPathTemplate = javaServer.proxyRequestPathTemplate != null,
            httpsSupport = "", // Not available in JavaServer
            allowedAppIds = javaServer.allowedAppIds.copyOf()
        )
    }
}
