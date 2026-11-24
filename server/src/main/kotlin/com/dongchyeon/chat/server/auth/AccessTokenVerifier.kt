package com.dongchyeon.chat.server.auth

import com.dongchyeon.chat.server.auth.JwtProvider.Companion.DEVICE_ID_CLAIM
import com.dongchyeon.chat.server.auth.JwtProvider.Companion.TYPE_CLAIM
import com.dongchyeon.chat.shared.error.ErrorCode
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import org.springframework.stereotype.Component

// HTTP 필터(ADR 0013)와 WebSocket AUTH 프레임(ADR 0031)이 같은 검증을 쓴다. DB는 조회하지 않는다
@Component
class AccessTokenVerifier(private val jwtProvider: JwtProvider) {

    /** 실패하면 JwtAuthenticationException(TOKEN_EXPIRED 또는 INVALID_TOKEN)을 던진다. */
    fun verify(token: String): VerifiedAccessToken {
        val claims = try {
            jwtProvider.parse(token)
        } catch (e: ExpiredJwtException) {
            // ExpiredJwtException은 JwtException의 자식이라 반드시 먼저 잡는다. 순서를 바꾸면 만료도 INVALID_TOKEN이 된다
            throw JwtAuthenticationException(ErrorCode.TOKEN_EXPIRED, "토큰이 만료되었습니다.", e)
        } catch (e: JwtException) {
            // 형식 오류(Malformed), 서명 불일치(Signature) 등 나머지를 부모 하나로 받는다
            throw JwtAuthenticationException(ErrorCode.INVALID_TOKEN, "유효하지 않은 토큰입니다.", e)
        }

        // ADR 0012: 두 토큰이 같은 키로 서명되므로 종류를 확인한다.
        // "REFRESH가 아니면 통과"가 아니라 "ACCESS일 때만 통과"로 써서 type 칸이 없는 토큰도 거부한다
        if (claims[TYPE_CLAIM] != TokenType.ACCESS.name) {
            throw JwtAuthenticationException(ErrorCode.INVALID_TOKEN, "유효하지 않은 토큰입니다.")
        }

        // ADR 0033 이전에 발급된 토큰은 deviceId가 없다. REST는 그대로 받고, WebSocket만 거절한다
        return VerifiedAccessToken(claims.subject.toLong(), claims[DEVICE_ID_CLAIM] as? String)
    }
}

data class VerifiedAccessToken(val userId: Long, val deviceId: String?)
