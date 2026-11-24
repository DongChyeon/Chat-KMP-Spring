package com.dongchyeon.chat.server.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtBuilder
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

enum class TokenType { ACCESS, REFRESH }

// ADR 0008(jjwt), 0012(HS256, 두 토큰 모두 JWT)
@Component
class JwtProvider(
    private val properties: JwtProperties,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(properties.secret.toByteArray())

    // ADR 0033: deviceId를 넣어 로그아웃할 때 그 기기의 WebSocket 연결만 닫을 수 있게 한다
    fun createAccessToken(userId: Long, deviceId: String, now: Instant = Instant.now()): String =
        create(userId, TokenType.ACCESS, now, now.plus(properties.accessTokenTtl)) { claim(DEVICE_ID_CLAIM, deviceId) }

    fun createRefreshToken(userId: Long, now: Instant = Instant.now()): IssuedToken {
        val expiresAt = now.plus(properties.refreshTokenTtl)
        return IssuedToken(create(userId, TokenType.REFRESH, now, expiresAt), expiresAt)
    }

    /** 서명과 만료 시간을 검증하고 클레임을 돌려준다. 검증에 실패하면 jjwt의 JwtException을 던진다. */
    fun parse(token: String): Claims =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload

    private fun create(
        userId: Long,
        type: TokenType,
        issuedAt: Instant,
        expiresAt: Instant,
        extraClaims: JwtBuilder.() -> Unit = {},
    ): String =
        Jwts.builder()
            .subject(userId.toString())
            // 두 토큰을 같은 키로 서명하므로 종류를 담아 둔다. 검증할 때 반드시 확인해야 한다 (ADR 0012)
            .claim(TYPE_CLAIM, type.name)
            // 같은 초에 두 번 발급해도 토큰 값이 달라지도록 고유 ID를 넣는다 (token_hash UNIQUE 제약)
            .id(java.util.UUID.randomUUID().toString())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .apply(extraClaims)
            // 알고리즘을 명시한다. 생략하면 jjwt가 키 길이를 보고 고르기 때문에 키가 길면 HS384/HS512가 된다 (ADR 0012: HS256)
            .signWith(key, Jwts.SIG.HS256)
            .compact()

    data class IssuedToken(val value: String, val expiresAt: Instant)

    companion object {
        const val TYPE_CLAIM = "type"
        const val DEVICE_ID_CLAIM = "deviceId"
    }
}
