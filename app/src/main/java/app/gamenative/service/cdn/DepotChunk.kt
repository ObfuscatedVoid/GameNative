package app.gamenative.service.cdn

import `in`.dragonbra.javasteam.types.ChunkData
import `in`.dragonbra.javasteam.util.Strings
import `in`.dragonbra.javasteam.util.VZipUtil
import `in`.dragonbra.javasteam.util.VZstdUtil
import `in`.dragonbra.javasteam.util.ZipUtil
import `in`.dragonbra.javasteam.util.crypto.CryptoHelper
import `in`.dragonbra.javasteam.util.stream.MemoryStream
import timber.log.Timber
import java.io.IOException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.util.function.Supplier
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException
import javax.crypto.ShortBufferException
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Depot Chunk decryption and decompression utilities
 * Matches com.xj.standalone.steam.cdn.DepotChunk
 */
object DepotChunk {
    private val ecbCipherThreadLocal: ThreadLocal<Cipher> = ThreadLocal.withInitial {
        Cipher.getInstance("AES/ECB/NoPadding", CryptoHelper.SEC_PROV)
    }
    
    private val cbcCipherThreadLocal: ThreadLocal<Cipher> = ThreadLocal.withInitial {
        Cipher.getInstance("AES/CBC/PKCS7Padding", CryptoHelper.SEC_PROV)
    }
    
    object a {
        /**
         * Decrypt and decompress chunk data
         * Matches DepotChunk.a.i() - line 122-196
         * 
         * @param chunk Chunk data info
         * @param compressedData Compressed chunk data from CDN
         * @param destination Destination buffer for uncompressed data
         * @param depotKey 32-byte depot decryption key
         * @return Uncompressed length
         */
        @Throws(
            BadPaddingException::class,
            NoSuchPaddingException::class,
            IllegalBlockSizeException::class,
            NoSuchAlgorithmException::class,
            InvalidKeyException::class,
            IOException::class,
            NoSuchProviderException::class,
            ShortBufferException::class,
            InvalidAlgorithmParameterException::class
        )
        fun decryptAndDecompress(
            chunk: ChunkData,
            compressedData: ByteArray,
            destination: ByteArray,
            depotKey: ByteArray
        ): Int {
            val startTime = System.currentTimeMillis()
            
            // Validate inputs
            if (destination.size < chunk.uncompressedLength) {
                throw IllegalArgumentException(
                    "The destination buffer must be longer than the chunk uncompressedLength."
                )
            }
            
            if (depotKey.size != 32) {
                throw IllegalArgumentException("Tried to decrypt depot chunk with non 32 byte key!")
            }
            
            // Step 1: Decrypt IV using AES/ECB/NoPadding
            val secretKeySpec = SecretKeySpec(depotKey, "AES")
            val ecbCipher = ecbCipherThreadLocal.get()
                ?: Cipher.getInstance("AES/ECB/NoPadding", CryptoHelper.SEC_PROV)
            
            ecbCipher.init(Cipher.DECRYPT_MODE, secretKeySpec)
            val iv = ByteArray(16)
            val ivDecrypted = ecbCipher.doFinal(compressedData, 0, 16, iv)
            
            if (ivDecrypted != 16) {
                throw IllegalArgumentException("Failed to decrypt depot chunk iv (16 != $ivDecrypted)")
            }
            
            // Step 2: Decrypt data using AES/CBC/PKCS7Padding
            val encryptedDataLength = compressedData.size - 16
            val decryptedData = ByteArray(encryptedDataLength)
            val cbcCipher = cbcCipherThreadLocal.get()
                ?: Cipher.getInstance("AES/CBC/PKCS7Padding", CryptoHelper.SEC_PROV)
            
            cbcCipher.init(Cipher.DECRYPT_MODE, secretKeySpec, IvParameterSpec(iv))
            val decryptedLength = cbcCipher.doFinal(
                compressedData,
                16,
                compressedData.size - 16,
                decryptedData
            )
            
            val decompressStartTime = System.currentTimeMillis()
            
            // Step 3: Decompress based on compression format
            val uncompressedLength = when {
                // VZip format (VZ header)
                decryptedLength > 1 && decryptedData[0] == 0x56.toByte() && decryptedData[1] == 0x5A.toByte() -> {
                    val memoryStream = MemoryStream(decryptedData, 0, decryptedLength)
                    try {
                        VZipUtil.decompress(memoryStream, destination, false)
                    } finally {
                        memoryStream.close()
                    }
                }
                // VZstd format (VSZa header)
                decryptedData[0] == 0x56.toByte() && decryptedData[1] == 0x53.toByte() &&
                decryptedData[2] == 0x5A.toByte() && decryptedData[3] == 0x61.toByte() -> {
                    val compressedSlice = decryptedData.sliceArray(0 until decryptedLength)
                    VZstdUtil.decompress(compressedSlice, destination, false)
                }
                // Standard zlib format
                else -> {
                    val memoryStream = MemoryStream(decryptedData, 0, decryptedLength)
                    try {
                        ZipUtil.decompress(memoryStream, destination, false)
                    } finally {
                        memoryStream.close()
                    }
                }
            }
            
            val decompressTime = System.currentTimeMillis() - decompressStartTime
            Timber.d("Decompression took ${decompressTime}ms")
            
            // Validate uncompressed length
            if (uncompressedLength != chunk.uncompressedLength) {
                throw IOException(
                    "Processed data checksum failed to decompress to the expected chunk uncompressed length. " +
                    "(was $uncompressedLength, should be ${chunk.uncompressedLength})"
                )
            }
            
            // Validate checksum (Adler32)
            val calculatedChecksum = calculateAdler32(destination, 0, uncompressedLength)
            if (calculatedChecksum != chunk.checksum) {
                throw IOException(
                    "Processed data checksum is incorrect ($calculatedChecksum != ${chunk.checksum})! " +
                    "Downloaded depot chunk is corrupt or invalid/wrong depot key?"
                )
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            Timber.d("Processed chunk ${Strings.toHex(chunk.chunkID)} in ${totalTime}ms")
            
            return uncompressedLength
        }
        
        /**
         * Calculate Adler32 checksum
         */
        private fun calculateAdler32(data: ByteArray, offset: Int, length: Int): Int {
            var s1 = 1
            var s2 = 0
            
            for (i in offset until (offset + length)) {
                s1 = (s1 + (data[i].toInt() and 0xFF)) % 65521
                s2 = (s2 + s1) % 65521
            }
            
            return (s2 shl 16) or s1
        }
    }
}

