package app.gamenative.service.contentdownloader

import java.util.concurrent.atomic.AtomicLong

/**
 * Global download statistics
 * Matches com.xj.standalone.steam.contentdownloader.GlobalDownloadStats
 */
data class GlobalDownloadStats(
    val sizeDownloaded: AtomicLong = AtomicLong(0),
    val sizeWrite: AtomicLong = AtomicLong(0),
    val prevSizeDownloaded: AtomicLong = AtomicLong(0),
    val prevSizeInstalled: AtomicLong = AtomicLong(0)
) {
    fun sizeDownloaded(): AtomicLong = sizeDownloaded
    fun sizeWrite(): AtomicLong = sizeWrite
    fun prevSizeDownloaded(): AtomicLong = prevSizeDownloaded
    fun prevSizeInstalled(): AtomicLong = prevSizeInstalled
}

