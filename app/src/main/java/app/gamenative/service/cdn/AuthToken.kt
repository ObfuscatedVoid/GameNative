package app.gamenative.service.cdn

import `in`.dragonbra.javasteam.enums.EResult
import java.util.Date

/**
 * CDN Authentication Token
 * Matches com.xj.standalone.steam.cdn.AuthToken
 */
data class AuthToken(
    val result: EResult,
    val token: String,
    val expiration: Date
) {
    fun result(): EResult = result
    fun token(): String = token
    fun expiration(): Date = expiration
}

