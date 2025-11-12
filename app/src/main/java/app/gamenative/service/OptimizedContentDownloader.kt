package app.gamenative.service

import app.gamenative.service.cdn.CDNClientPool
import app.gamenative.service.cdn.DepotChunk
import app.gamenative.service.callback.InternalDownloadCallback
import app.gamenative.service.content.SteamContent
import app.gamenative.service.contentdownloader.DepotDownloadStats
import app.gamenative.service.contentdownloader.GlobalDownloadStats
import `in`.dragonbra.javasteam.steam.contentdownloader.ContentDownloader
import `in`.dragonbra.javasteam.steam.handlers.steamcontent.SteamContent as JavaSteamContent
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.types.ChunkData
import `in`.dragonbra.javasteam.types.FileData
import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.enums.EDepotFileFlag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Data structures matching decompiled ContentDownloader.java
 */

/**
 * DepotDownloadInfo - Contains depot download information
 * Matches com.xj.standalone.steam.contentdownloader.DepotDownloadInfo
 */
private data class DepotDownloadInfo(
    val depotId: Int,
    val appId: Int,
    val manifestGID: Long,
    val branch: String,
    val installPath: String,
    val decryptionKey: ByteArray? = null
) {
    fun depotId(): Int = depotId
    fun decryptionKey(): ByteArray? = decryptionKey
    fun installPath(): String = installPath
}

// Note: GlobalDownloadStats and DepotDownloadStats are now imported from
// app.gamenative.service.contentdownloader package

/**
 * DepotFilesData - Contains depot files data
 * Matches com.xj.standalone.steam.contentdownloader.DepotFilesData
 */
private data class DepotFilesData(
    val depotDownloadInfo: DepotDownloadInfo,
    val globalDownloadStats: GlobalDownloadStats,
    val depotDownloadStats: DepotDownloadStats,
    val manifest: DepotManifest
) {
    fun depotDownloadInfo(): DepotDownloadInfo = depotDownloadInfo
    fun depotDownloadStats(): DepotDownloadStats = depotDownloadStats
    fun manifest(): DepotManifest = manifest
    fun globalDownloadStats(): GlobalDownloadStats = globalDownloadStats
}

/**
 * FileStreamData - Contains file stream data for writing chunks
 * Matches com.xj.standalone.steam.contentdownloader.FileStreamData
 */
private class FileStreamData(
    var fileChannel: FileChannel? = null,
    var chunksToWrite: Int = 0,
    val isEnd: Boolean = false,
    val needDownloadChunks: List<ChunkData>,
    val fileData: FileData,
    val chunksToDownload: AtomicInteger = AtomicInteger(0),
    val chunksToWriteAtomic: AtomicInteger = AtomicInteger(0),
    val hasSubmittedForDecompress: AtomicBoolean = AtomicBoolean(false),
    val hasStartedDecompress: AtomicBoolean = AtomicBoolean(false),
    val decompressLock: Any = Any(),
    val fileChannelRef: AtomicReference<FileChannel?> = AtomicReference(null)
) {
    fun fileChannel(): FileChannel? = fileChannel
    fun chunksToWrite(): Int = chunksToWrite
}

/**
 * Optimized ContentDownloader wrapper that provides:
 * - High download speed through Flow-based concurrency
 * - Low CPU usage through CPU-aware task limits
 * - Sequential checksum validation matching GameHub's implementation
 *
 * Based on optimizations from GameHub's ContentDownloader (decompiled)
 *
 * Flow Pipeline Structure (matching decompiled code):
 * 1. Buffer: maxDownloads capacity (24)
 * 2. Stage 1: flatMapMerge with maxDownloads * 2 concurrency (48) - prepare chunks
 * 3. Stage 2: flatMapMerge with maxDownloads concurrency (24) - download chunks
 * 4. Buffer: maxDownloads capacity (24)
 * 5. Stage 3: flatMapMerge with maxDownloads concurrency (24) - download chunk data
 * 6. Stage 4: flatMapMerge with CPU_TASK_PROCESS_SIZE concurrency (12) - process/write chunks
 */
class OptimizedContentDownloader(
    private val steamClient: SteamClient,
    private val scope: CoroutineScope
) {
    companion object {
        // Default max download concurrency (matches SteamConfig default: 24)
        private const val DEFAULT_MAX_DOWNLOAD = 24

        // CPU task processing size - limits CPU-intensive work
        // Capped at 12 to prevent CPU overload (matches Math.min(12, SteamConfig.c()))
        private val CPU_TASK_PROCESS_SIZE: Int
            get() = minOf(12, Runtime.getRuntime().availableProcessors())

        // Staging file suffix (matches decompiled code: method k())
        private const val STAGING_FILE_SUFFIX = ".staging^"
    }

    private val cpuDispatcher: ExecutorCoroutineDispatcher =
        Executors.newFixedThreadPool(CPU_TASK_PROCESS_SIZE)
            .asCoroutineDispatcher()

    /**
     * Download a single depot with optimized Flow pipeline (matching decompiled method f() line 298)
     *
     * Implements the multi-stage Flow pipeline from ContentDownloader.java line 369:
     * - Buffer: maxDownloads capacity
     * - Stage 1: flatMapMerge(maxDownloads * 2) - Prepare FileStreamData (chunk validation)
     * - Stage 2: flatMapMerge(maxDownloads) - Process FileStreamData (tracking)
     * - Buffer: maxDownloads capacity again
     * - Stage 3: flatMapMerge(maxDownloads) - Download chunk data
     * - Stage 4: flatMapMerge(CPU_TASK_PROCESS_SIZE) - Process and write chunks
     *
     * @param appId Application ID
     * @param depotId Depot ID to download
     * @param depotManifest Depot manifest (must be fetched separately)
     * @param installPath Installation path
     * @param branch Branch name (e.g., "public")
     * @param depotKey Decryption key for encrypted depots (optional)
     * @param maxDownloads Maximum concurrent downloads (default: 24)
     * @param internalDownloadCallback Internal download callback for progress updates (optional)
     * @param parentScope Parent coroutine scope
     * @return true if download succeeded, false otherwise
     */
    suspend fun downloadApp(
        appId: Int,
        depotId: Int,
        depotManifest: DepotManifest,
        installPath: String,
        branch: String,
        depotKey: ByteArray? = null,
        maxDownloads: Int = DEFAULT_MAX_DOWNLOAD,
        internalDownloadCallback: InternalDownloadCallback? = null,
        parentScope: CoroutineScope
    ): Boolean {
        var cdnClientPool: CDNClientPool? = null
        return try {
            // Check if parent scope is active
            if (!parentScope.isActive) {
                Timber.d("App($appId,$depotId) was not completely downloaded. Operation was canceled.")
                return false
            }

            // Get SteamContent handler from SteamClient
            val javaSteamContent = steamClient.getHandler(JavaSteamContent::class.java)
                ?: throw IllegalStateException("SteamContent handler not found")
            val steamContent = SteamContent(javaSteamContent)

            // Create CDNClientPool
            cdnClientPool = CDNClientPool(steamContent, appId, parentScope)

            // Create download stats
            val globalDownloadStats = GlobalDownloadStats()
            val depotDownloadStats = DepotDownloadStats()

            // Create DepotDownloadInfo
            val depotDownloadInfo = DepotDownloadInfo(
                depotId = depotId,
                appId = appId,
                manifestGID = depotManifest.manifestGID,
                branch = branch,
                installPath = installPath,
                decryptionKey = depotKey
            )

            // Create DepotFilesData
            val depotFilesData = DepotFilesData(
                depotDownloadInfo = depotDownloadInfo,
                globalDownloadStats = globalDownloadStats,
                depotDownloadStats = depotDownloadStats,
                manifest = depotManifest
            )

            // Process files from manifest (matching ContentDownloader.java lines 331-357)
            val installScripts = mutableListOf<String>()
            val unknownInstallScripts = mutableListOf<String>()
            val fileList = mutableListOf<FileData>()

            for (fileData in depotManifest.files) {
                val finalFilePath = Paths.get(installPath, fileData.fileName).toString()
                
                // Detect install scripts (matching ContentDownloader.d() line 196)
                detectInstallScripts(appId, finalFilePath, installScripts, unknownInstallScripts, fileData)

                val flags = fileData.flags
                val isDirectory = flags.contains(EDepotFileFlag.Directory)

                if (isDirectory) {
                    // Create directory
                    File(finalFilePath).mkdirs()
                } else {
                    // Create parent directory
                    File(finalFilePath).parentFile?.mkdirs()
                    // Add to file list (only non-directory files)
                    fileList.add(fileData)
                }
            }

            // Update install scripts if found (matching line 362)
            // Note: This would require SteamModuleDownloadEntity integration
            // For now, we'll just log them
            if (installScripts.isNotEmpty() || unknownInstallScripts.isNotEmpty()) {
                Timber.d("Found install scripts for depot $depotId: $installScripts, unknown: $unknownInstallScripts")
            }

            // Calculate CPU task processing size (matching line 327)
            val cpuTaskProcessSize = minOf(12, Runtime.getRuntime().availableProcessors())

            // Log download start (matching line 329)
            Timber.i("Downloading (maxDownload:$maxDownloads, cpuTaskProcessSize:$cpuTaskProcessSize) depot curDownloadAppId = $appId - depot = $depotId")

            // Flow pipeline matching decompiled code line 369 exactly
            fileList.asFlow()
                .buffer(maxDownloads, BufferOverflow.SUSPEND)  // Initial buffer
                .flatMapMerge(maxDownloads * 2) { fileData ->  // Stage 1: Prepare chunks (2x concurrency)
                    flow {
                        val fileStreamData = prepareDepotFileChunksSync(
                            depotFilesData,
                            fileData,
                            parentScope,
                            internalDownloadCallback,
                            appId,
                            depotId
                        )
                        if (fileStreamData != null && fileStreamData.needDownloadChunks.isNotEmpty()) {
                            emit(fileStreamData)
                        }
                    }.flowOn(Dispatchers.IO)
                }
                .flatMapMerge(maxDownloads) { fileStreamData ->  // Stage 2: Process FileStreamData
                    flow {
                        // Emit chunks to download
                        for (chunk in fileStreamData.needDownloadChunks) {
                            emit(ChunkDownloadRequest(fileStreamData, fileStreamData.fileData, chunk))
                        }
                    }
                }
                .buffer(maxDownloads, BufferOverflow.SUSPEND)  // Buffer before download
                .flatMapMerge(maxDownloads) { request ->  // Stage 3: Download chunks
                    val (fileStreamData, fileData, chunk) = request
                    flow {
                        val chunkData = downloadSteam3DepotFileChunkFile(
                            chunk,
                            cdnClientPool,
                            depotFilesData,
                            parentScope,
                            internalDownloadCallback,
                            appId,
                            depotId
                        )
                        if (chunkData != null) {
                            emit(ChunkDownloadResult(fileStreamData, fileData, chunk, chunkData))
                        }
                    }.flowOn(Dispatchers.IO)
                }
                .flatMapMerge(cpuTaskProcessSize) { result ->  // Stage 4: Process/write
                    val (fileStreamData, fileData, chunk, chunkData) = result
                    flow {
                        processAndWriteChunkFile(
                            depotFilesData,
                            fileData,
                            fileStreamData,
                            chunk,
                            chunkData,
                            parentScope
                        )
                        emit(Unit)
                    }.flowOn(cpuDispatcher)
                }
                .collect()  // Collect all results

            // Log completion (matching line 412)
            Timber.d("downloadDepotFiles $depotId finish")

            // Invoke callback with completion status
            if (internalDownloadCallback != null) {
                InternalDownloadCallback.invoke(
                    callback = internalDownloadCallback,
                    entity = Unit,
                    appId = appId,
                    depotId = depotId,
                    stats = depotFilesData.depotDownloadStats(),
                    isComplete = true
                )
            }

            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to download depot $depotId for app $appId")
            false
        } finally {
            // Cleanup CDNClientPool
            cdnClientPool?.close()
        }
    }

    /**
     * Validate file chunks sequentially (matching decompiled code method l())
     *
     * The decompiled code validates chunks sequentially in a loop, not in parallel.
     * This matches the original implementation from ContentDownloader.java:836-881
     *
     * @param file RandomAccessFile to validate
     * @param chunks List of chunks to validate
     * @param scope Coroutine scope for cancellation checks
     * @return List of chunks that failed validation and need re-download
     */
    suspend fun validateChunksSequential(
        file: RandomAccessFile,
        chunks: List<ChunkData>,
        scope: CoroutineScope
    ): List<ChunkData> {
        val invalidChunks = mutableListOf<ChunkData>()
        val fileLength = file.length()

        // Sequential validation matching decompiled code (lines 847-878)
        for (chunk in chunks) {
            // Check if scope is still active (matches CoroutineScopeKt.h check at line 849)
            if (!scope.isActive) {
                break
            }

            // Check file length first (matches line 852)
            if (chunk.offset + chunk.uncompressedLength > fileLength) {
                invalidChunks.add(chunk)
                Timber.d("Chunk ${chunk.chunkID} length mismatch, needs re-download")
                continue
            }

            // Calculate Adler32 checksum using RandomAccessFile (matches SDL.b() at line 861)
            val calculatedChecksum = calculateAdler32FromFile(
                file,
                chunk.offset,
                chunk.uncompressedLength
            )

            if (calculatedChecksum != chunk.checksum) {
                invalidChunks.add(chunk)
                Timber.d("Chunk ${chunk.chunkID} checksum mismatch, needs re-download")
            } else {
                Timber.d("Chunk ${chunk.chunkID} validation passed")
            }
        }

        return invalidChunks
    }

    /**
     * Calculate Adler32 checksum from RandomAccessFile (matching SDL.b() implementation)
     *
     * This matches the decompiled code at SDL.java:29-47 exactly
     * Uses RandomAccessFile directly, reading in 65536 byte chunks
     *
     * @param file RandomAccessFile to read from
     * @param offset Starting offset
     * @param length Length of data to checksum
     * @return Adler32 checksum value
     */
    private fun calculateAdler32FromFile(
        file: RandomAccessFile,
        offset: Long,
        length: Int
    ): Int {
        val buffer = ByteArray(65536)
        file.channel.position(offset)  // Set position first (matches line 32)

        var s1 = 0  // i2 in decompiled code
        var s2 = 0  // i3 in decompiled code
        var remaining = length

        while (remaining > 0) {
            val readSize = minOf(65536, remaining)  // iMin in decompiled code
            val bytesRead = file.read(buffer, 0, readSize)  // Matches line 37

            // Process bytes (matching SDL.b() logic at lines 39-42 exactly)
            // Note: The decompiled code processes all readSize bytes, using 0 if not read
            for (i in 0 until readSize) {
                val byte = if (i < bytesRead) buffer[i].toInt() and 0xFF else 0
                s1 = (s1 + byte) % 65521
                s2 = (s2 + s1) % 65521
            }

            remaining -= readSize
        }

        // Return format: (s2 << 16) | s1 (matches line 46 exactly)
        return (s2 shl 16) or s1
    }

    /**
     * Get staging file path (matching decompiled code method k())
     *
     * @param filePath Original file path
     * @return Staging file path with .staging^ suffix
     */
    fun getStagingFilePath(filePath: String): String {
        return filePath + STAGING_FILE_SUFFIX
    }

    /**
     * Detect install scripts (matching decompiled code method d() line 196)
     *
     * Matches ContentDownloader.d() implementation:
     * - If file.flags contains EDepotFileFlag.InstallScript, add to installScripts
     * - If filename contains "installscript" (case-insensitive) and ends with ".vdf", add to unknownInstallScripts
     * - Log findings
     *
     * @param appId Application ID
     * @param finalFilePath Final file path
     * @param installScripts List to add known install scripts to
     * @param unknownInstallScripts List to add unknown install scripts to
     * @param file File data to check
     */
    private fun detectInstallScripts(
        appId: Int,
        finalFilePath: String,
        installScripts: MutableList<String>,
        unknownInstallScripts: MutableList<String>,
        file: FileData
    ) {
        if (file.flags.contains(EDepotFileFlag.InstallScript)) {
            Timber.d("Find InstallScript for ($appId) -> ${file.fileName}, flags = ${file.flags}")
            installScripts.add(file.fileName)
            return
        }

        val fileName = file.fileName.lowercase()
        if (fileName.contains("installscript", ignoreCase = true) && fileName.endsWith(".vdf", ignoreCase = true)) {
            unknownInstallScripts.add(finalFilePath)
            return
        }

        if (fileName.contains("installscript", ignoreCase = true)) {
            Timber.d("Find InstallScript for ($appId) -> ${file.fileName}, flags(eif) = ${file.flags}")
        }
    }

    /**
     * Prepare depot file chunks synchronously (matching decompiled method i() lines 641-803)
     *
     * Validates existing staging files and creates FileStreamData with validated chunks
     *
     * @param depotFilesData Depot files data
     * @param fileData File data to prepare
     * @param scope Coroutine scope for cancellation checks
     * @param internalDownloadCallback Internal download callback for progress updates (optional)
     * @param appId Application ID (for callback)
     * @param depotId Depot ID (for callback)
     * @return FileStreamData with validated chunks, or null if cancelled
     */
    private suspend fun prepareDepotFileChunksSync(
        depotFilesData: DepotFilesData,
        fileData: FileData,
        scope: CoroutineScope,
        internalDownloadCallback: InternalDownloadCallback? = null,
        appId: Int? = null,
        depotId: Int? = null
    ): FileStreamData? {
        if (!scope.isActive) {
            Timber.d("prepareDepotFileChunksSync Download Scope is not active")
            return null
        }

        val depotDownloadStats = depotFilesData.depotDownloadStats()
        val finalFilePath = Paths.get(depotFilesData.depotDownloadInfo().installPath(), fileData.fileName).toString()
        val stagingFilePath = getStagingFilePath(finalFilePath)

        // Calculate total uncompressed length
        var totalUncompressedLength = 0L
        for (chunk in fileData.chunks) {
            totalUncompressedLength += chunk.uncompressedLength
        }

        val stagingFile = File(stagingFilePath)
        val stagingFileExists = stagingFile.exists()

        // Handle empty files or files with no chunks
        // Note: The decompiled Java code checks totalSize <= 0 (due to decompilation artifact),
        // but the correct check is: empty file if totalSize == 0 OR chunks.isEmpty()
        // We should NOT compare totalSize to totalUncompressedLength as they should be equal for normal files
        if (fileData.totalSize == 0L || fileData.chunks.isEmpty()) {
            Timber.d("Empty file: ${fileData.fileName}, no download needed")
            if (stagingFileExists) {
                stagingFile.renameTo(File(finalFilePath))
            } else {
                val finalFile = File(finalFilePath)
                if (!finalFile.exists()) {
                    finalFile.createNewFile()
                }
            }
            return FileStreamData(
                fileChannel = null,
                chunksToWrite = 0,
                isEnd = false,
                needDownloadChunks = emptyList(),
                fileData = fileData
            )
        }

        // Check if final file already exists
        val finalFile = File(finalFilePath)
        if (finalFile.exists()) {
            depotFilesData.globalDownloadStats().sizeWrite().addAndGet(totalUncompressedLength)
            
            // Call callback if provided (matching decompiled code lines 700-716)
            if (internalDownloadCallback != null && appId != null && depotId != null) {
                InternalDownloadCallback.invoke(
                    callback = internalDownloadCallback,
                    entity = Unit, // Would be SteamModuleDownloadEntity in actual implementation
                    appId = appId,
                    depotId = depotId,
                    stats = depotFilesData.depotDownloadStats(),
                    isComplete = false
                )
            }
            
            Timber.d("File already exists, no re-download needed: ${fileData.fileName}, size = ${fileData.chunks.size}")
            return FileStreamData(
                fileChannel = null,
                chunksToWrite = 0,
                isEnd = false,
                needDownloadChunks = emptyList(),
                fileData = fileData
            )
        }

        // Validate chunks in staging file or create new staging file
        val chunksToDownload: List<ChunkData>

        if (stagingFileExists) {
            RandomAccessFile(stagingFilePath, "rw").use { raf ->
                // Truncate if size mismatch
                if (stagingFile.length() != fileData.totalSize) {
                    raf.channel.truncate(fileData.totalSize)
                }
                // Validate chunks sequentially
                chunksToDownload = validateChunksSequential(raf, fileData.chunks, scope)
            }
        } else {
            // Create new staging file
            FileOutputStream(stagingFilePath).use { fos ->
                fos.channel.truncate(fileData.totalSize)
            }
            chunksToDownload = fileData.chunks.toList()
        }

        // Update stats based on chunks that need download
        if (chunksToDownload.isEmpty()) {
            depotFilesData.globalDownloadStats().sizeWrite().addAndGet(totalUncompressedLength)
        } else {
            var remainingUncompressedLength = 0L
            for (chunk in chunksToDownload) {
                remainingUncompressedLength += chunk.uncompressedLength
            }
            depotFilesData.globalDownloadStats().sizeWrite().addAndGet(totalUncompressedLength - remainingUncompressedLength)
        }

        // Call callback if provided (matching decompiled code lines 761-777)
        if (internalDownloadCallback != null && appId != null && depotId != null) {
            InternalDownloadCallback.invoke(
                callback = internalDownloadCallback,
                entity = Unit, // Would be SteamModuleDownloadEntity in actual implementation
                appId = appId,
                depotId = depotId,
                stats = depotFilesData.depotDownloadStats(),
                isComplete = false
            )
        }

        return FileStreamData(
            fileChannel = null,
            chunksToWrite = chunksToDownload.size,
            isEnd = false,
            needDownloadChunks = chunksToDownload,
            fileData = fileData
        )
    }

    /**
     * Download Steam3 depot file chunk (matching decompiled method g() lines 420-539)
     *
     * Uses CDNClientPool to download chunk data
     *
     * @param chunkData Chunk data to download
     * @param cdnClientPool CDN client pool
     * @param depotFilesData Depot files data
     * @param scope Coroutine scope for cancellation checks
     * @param internalDownloadCallback Internal download callback for progress updates (optional)
     * @param appId Application ID (for callback)
     * @param depotId Depot ID (for callback)
     * @return Byte array containing chunk data, or null if cancelled
     */
    private suspend fun downloadSteam3DepotFileChunkFile(
        chunkData: ChunkData,
        cdnClientPool: CDNClientPool,
        depotFilesData: DepotFilesData,
        scope: CoroutineScope,
        internalDownloadCallback: InternalDownloadCallback? = null,
        appId: Int? = null,
        depotId: Int? = null
    ): ByteArray? {
        if (!scope.isActive) {
            return null
        }

        try {
            // Get client from pool
            val client = cdnClientPool.getClient()
            
            // Get depot ID (use parameter if provided, otherwise from depotFilesData)
            val actualDepotId = depotId ?: depotFilesData.depotDownloadInfo().depotId()
            
            // Get CDN server
            val cdnServer = cdnClientPool.getCurrentServer()
                ?: cdnClientPool.selectBestCDNServer()
            
            // Get global stats
            val globalStats = depotFilesData.globalDownloadStats()
            
            // Get max connections
            val maxConnections = cdnClientPool.getMaxConnections()
            
            // Get app ID (use parameter if provided, otherwise from depotFilesData)
            val actualAppId = appId ?: depotFilesData.depotDownloadInfo().appId
            
            // Download chunk
            val chunkDataBytes = client.downloadChunk(
                maxConnections = maxConnections,
                appId = actualAppId,
                depotId = actualDepotId,
                chunkData = chunkData,
                cdnClientPool = cdnClientPool,
                cdnServer = cdnServer,
                globalStats = globalStats
            )
            
            // Update depot stats with compressed length
            depotFilesData.depotDownloadStats().sizeDownloaded().addAndGet(chunkData.compressedLength.toLong())
            
            // Invoke callback after chunk download (matching Java line 534)
            if (internalDownloadCallback != null && actualAppId != null && actualDepotId != null) {
                InternalDownloadCallback.invoke(
                    callback = internalDownloadCallback,
                    entity = Unit,
                    appId = actualAppId,
                    depotId = actualDepotId,
                    stats = depotFilesData.depotDownloadStats(),
                    isComplete = false
                )
            }
            
            return chunkDataBytes
        } catch (e: Exception) {
            Timber.e(e, "Failed to download chunk ${chunkData.chunkID}")
            throw IllegalStateException("Failed to download chunk ${chunkData.chunkID}: ${e.message}", e)
        }
    }

    /**
     * Process and write chunk file (matching decompiled method j() lines 805-830)
     *
     * Decrypts chunk if needed and writes to disk
     *
     * @param depotFilesData Depot files data
     * @param fileData File data
     * @param fileStreamData File stream data
     * @param chunkData Chunk data
     * @param chunkDataBytes Downloaded chunk data bytes
     * @param scope Coroutine scope for cancellation checks
     */
    private fun processAndWriteChunkFile(
        depotFilesData: DepotFilesData,
        fileData: FileData,
        fileStreamData: FileStreamData,
        chunkData: ChunkData,
        chunkDataBytes: ByteArray,
        scope: CoroutineScope
    ) {
        if (!scope.isActive) {
            return
        }

        val depotDownloadInfo = depotFilesData.depotDownloadInfo()
        val decryptionKey = depotDownloadInfo.decryptionKey()

        Timber.d("processAndWriteChunkFile needs decryption: ${decryptionKey != null}")

        val decryptedData: ByteArray = if (decryptionKey != null) {
            // Decrypt and decompress chunk using DepotChunk
            val decryptedBuffer = ByteArray(chunkData.uncompressedLength)
            try {
                val uncompressedLength = DepotChunk.a.decryptAndDecompress(
                    chunk = chunkData,
                    compressedData = chunkDataBytes,
                    destination = decryptedBuffer,
                    depotKey = decryptionKey
                )
                
                if (uncompressedLength != chunkData.uncompressedLength) {
                    throw IllegalStateException(
                        "Decompressed length mismatch: expected ${chunkData.uncompressedLength}, got $uncompressedLength"
                    )
                }
                
                decryptedBuffer
            } catch (e: Exception) {
                Timber.e(e, "Failed to decrypt/decompress chunk ${chunkData.chunkID}")
                throw IllegalStateException("Failed to decrypt chunk ${chunkData.chunkID}: ${e.message}", e)
            }
        } else {
            chunkDataBytes
        }

        writeFileChunk(
            depotFilesData.globalDownloadStats(),
            depotDownloadInfo,
            fileStreamData,
            fileData,
            chunkData,
            decryptedData
        )
    }

    /**
     * Write file chunk to disk (matching decompiled method m() lines 883-924)
     *
     * Thread-safe file writing with synchronization on FileStreamData
     *
     * @param globalDownloadStats Global download statistics
     * @param depotDownloadInfo Depot download info
     * @param fileStreamData File stream data
     * @param fileData File data
     * @param chunkData Chunk data
     * @param chunkBytes Chunk bytes to write
     */
    private fun writeFileChunk(
        globalDownloadStats: GlobalDownloadStats,
        depotDownloadInfo: DepotDownloadInfo,
        fileStreamData: FileStreamData,
        fileData: FileData,
        chunkData: ChunkData,
        chunkBytes: ByteArray
    ) {
        synchronized(fileStreamData) {
            try {
                val startTime = System.currentTimeMillis()
                val stagingFilePath = getStagingFilePath(
                    Paths.get(depotDownloadInfo.installPath(), fileData.fileName).toString()
                )

                // Open file channel if not already open
                if (fileStreamData.fileChannel() == null) {
                    val raf = RandomAccessFile(stagingFilePath, "rw")
                    fileStreamData.fileChannel = raf.channel
                }

                val fileChannel = fileStreamData.fileChannel()
                if (fileChannel != null) {
                    // Write chunk at correct offset
                    fileChannel.position(chunkData.offset)
                    fileChannel.write(ByteBuffer.wrap(chunkBytes))

                    // Update global stats
                    globalDownloadStats.sizeWrite().addAndGet(chunkData.uncompressedLength.toLong())

                    // Decrement remaining chunks counter
                    val remainingChunks = fileStreamData.chunksToWrite() - 1
                    fileStreamData.chunksToWrite = remainingChunks

                    // If all chunks written, rename staging file to final file
                    if (remainingChunks == 0) {
                        fileChannel.close()
                        val finalFilePath = Paths.get(depotDownloadInfo.installPath(), fileData.fileName).toString()
                        File(stagingFilePath).renameTo(File(finalFilePath))
                    }
                }

                val elapsed = System.currentTimeMillis() - startTime
                Timber.d("writeFileChunk(${chunkData.chunkID}) finish -> ${elapsed} ms")
            } catch (e: IOException) {
                // Handle NotEnoughSpaceException if needed
                // In actual implementation: check SDL.a.e(e) for NotEnoughSpaceException
                Timber.e(e, "Failed to write chunk ${chunkData.chunkID}")
                throw e
            }
        }
    }

    /**
     * Download multiple depots in parallel with Flow-based concurrency
     * This provides optimal download speed while managing CPU usage
     *
     * The Flow pipeline structure matches the decompiled code's multi-stage approach:
     * - Buffer with maxDownloads capacity
     * - Multiple flatMapMerge stages with different concurrency levels
     * - Final stage uses CPU_TASK_PROCESS_SIZE for CPU-intensive work
     *
     * @param appId Application ID
     * @param depotManifests Map of depot ID to depot manifest (must be fetched separately)
     * @param installPath Installation path
     * @param branch Branch name
     * @param depotKeys Map of depot ID to decryption key (optional, for encrypted depots)
     * @param maxDownloads Maximum concurrent downloads per depot
     * @param internalDownloadCallback Internal download callback for progress updates (optional)
     * @return Map of depotId to success status
     */
    suspend fun downloadDepotsParallel(
        appId: Int,
        depotManifests: Map<Int, DepotManifest>,
        installPath: String,
        branch: String,
        depotKeys: Map<Int, ByteArray> = emptyMap(),
        maxDownloads: Int = DEFAULT_MAX_DOWNLOAD,
        internalDownloadCallback: InternalDownloadCallback? = null
    ): Map<Int, Boolean> = coroutineScope {
        val results = Collections.synchronizedMap(mutableMapOf<Int, Boolean>())

        // Use Flow for parallel depot downloads with concurrency control
        // Structure matches decompiled code's multi-stage Flow pipeline
        depotManifests.entries.asFlow()
            .buffer(maxDownloads, BufferOverflow.SUSPEND)  // Stage 0: Buffer with maxDownloads capacity
            .flowOn(Dispatchers.IO)  // Use I/O dispatcher for network operations
            .flatMapMerge(maxDownloads * 2) { entry ->  // Stage 1: Higher concurrency for preparation
                val depotId = entry.key
                val depotManifest = entry.value
                kotlinx.coroutines.flow.flow {
                    try {
                        Timber.i("Starting download for depot $depotId")

                        val success = downloadApp(
                            appId = appId,
                            depotId = depotId,
                            depotManifest = depotManifest,
                            installPath = installPath,
                            branch = branch,
                            depotKey = depotKeys[depotId],
                            maxDownloads = maxDownloads,
                            internalDownloadCallback = internalDownloadCallback,
                            parentScope = this@coroutineScope
                        )

                        results[depotId] = success
                        emit(DepotDownloadResult(depotId, success))

                        if (success) {
                            Timber.i("Successfully downloaded depot $depotId")
                        } else {
                            Timber.w("Failed to download depot $depotId")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error downloading depot $depotId")
                        results[depotId] = false
                        emit(DepotDownloadResult(depotId, false))
                    }
                }.flowOn(Dispatchers.IO)
            }
            .buffer(maxDownloads, BufferOverflow.SUSPEND)  // Stage 2: Buffer again before processing
            .flatMapMerge(CPU_TASK_PROCESS_SIZE) { result ->  // Stage 3: Lower concurrency for CPU-intensive work
                kotlinx.coroutines.flow.flow {
                    // Final processing stage with CPU-limited concurrency
                    emit(result)
                }.flowOn(cpuDispatcher)
            }
            .collect()  // Collect all results

        results
    }

    /**
     * Clean up resources
     */
    fun close() {
        cpuDispatcher.close()
    }

    /**
     * Data class for depot download results
     */
    private data class DepotDownloadResult(
        val depotId: Int,
        val success: Boolean
    )

    /**
     * Data class for chunk download request (used in Flow pipeline Stage 2->3)
     */
    private data class ChunkDownloadRequest(
        val fileStreamData: FileStreamData,
        val fileData: FileData,
        val chunk: ChunkData
    )

    /**
     * Data class for chunk download results (used in Flow pipeline Stage 3->4)
     */
    private data class ChunkDownloadResult(
        val fileStreamData: FileStreamData,
        val fileData: FileData,
        val chunk: ChunkData,
        val chunkData: ByteArray
    )
}
