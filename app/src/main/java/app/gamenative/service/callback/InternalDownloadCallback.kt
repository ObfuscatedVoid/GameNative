package app.gamenative.service.callback

import app.gamenative.service.contentdownloader.DepotDownloadStats
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * Internal download callback interface for progress updates
 * Matches com.xj.standalone.steam.core.InternalDownloadCallback
 */
interface InternalDownloadCallback {
    /**
     * Called when download progress updates
     * 
     * @param entity Download entity (can be Any for now)
     * @param appId Application ID
     * @param depotId Depot ID
     * @param stats Depot download statistics
     * @param isComplete Whether download is complete
     */
    suspend fun onDownloadProgress(
        entity: Any,
        appId: Int,
        depotId: Int,
        stats: DepotDownloadStats,
        isComplete: Boolean
    )
    
    companion object {
        /**
         * Helper method to invoke callback
         * Matches InternalDownloadCallback.b() - line 534 in ContentDownloader.java
         */
        suspend fun invoke(
            callback: InternalDownloadCallback?,
            entity: Any,
            appId: Int,
            depotId: Int,
            stats: DepotDownloadStats,
            isComplete: Boolean,
            flags: Int = 0,
            marker: Any? = null
        ) {
            if (callback != null) {
                callback.onDownloadProgress(entity, appId, depotId, stats, isComplete)
            }
        }
    }
}

