package app.gamenative.service.contentdownloader

import java.util.concurrent.atomic.AtomicLong

/**
 * Depot-specific download statistics
 * Matches com.xj.standalone.steam.contentdownloader.DepotDownloadStats
 */
data class DepotDownloadStats(
    val sizeDownloaded: AtomicLong = AtomicLong(0),
    val sizeWrite: AtomicLong = AtomicLong(0)
) {
    fun sizeDownloaded(): AtomicLong = sizeDownloaded
    fun sizeWrite(): AtomicLong = sizeWrite
}

