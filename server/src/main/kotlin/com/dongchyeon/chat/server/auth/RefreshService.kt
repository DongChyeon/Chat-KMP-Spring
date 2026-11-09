package com.dongchyeon.chat.server.auth

import com.dongchyeon.chat.server.auth.JwtProvider.Companion.TYPE_CLAIM
import com.dongchyeon.chat.server.common.ApiException
import com.dongchyeon.chat.shared.auth.RefreshRequest
import com.dongchyeon.chat.shared.auth.TokenResponse
import com.dongchyeon.chat.shared.error.ErrorCode
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RefreshService(
    private val jwtProvider: JwtProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun refresh(request: RefreshRequest): TokenResponse {
        val userId = verify(request.refreshToken)
        // ADR 0010: Refresh Token은 교체하지 않고 받은 값을 그대로 돌려준다
        return TokenResponse(
            accessToken = jwtProvider.createAccessToken(userId),
            refreshToken = request.refreshToken,
        )
    }

    /** Refresh Token을 검증하고 userId를 돌려준다. 실패 원인은 로그에만 남기고 [refreshFailed]를 던진다 (ADR 0014). */
    private fun verify(refreshToken: String): Long {
        val claims = try {
            jwtProvider.parse(refreshToken)
        } catch (e: ExpiredJwtException) {
            // ExpiredJwtException은 JwtException의 자식이라 먼저 잡아야 원인을 구분할 수 있다
            log.info("refresh failed: expired")
            throw refreshFailed()
        } catch (e: JwtException) {
            // 형식 오류(Malformed), 서명 불일치(Signature) 등 나머지를 부모 하나로 받는다
            log.info("refresh failed: invalid jwt ({})", e.javaClass.simpleName)
            throw refreshFailed()
        }

        // "REFRESH일 때만 통과"로 써서 type 칸이 없는 토큰도 거부한다
        if (claims[TYPE_CLAIM] != TokenType.REFRESH.name) {
            log.info("refresh failed: wrong type ({})", claims[TYPE_CLAIM])
            throw refreshFailed()
        }

        if (refreshTokenRepository.findByTokenHash(TokenHasher.hash(refreshToken)) == null) {
            log.info("refresh failed: not in allowlist (userId={})", claims.subject)
            throw refreshFailed()
        }

        return claims.subject.toLong()
    }

    private fun refreshFailed() =
        ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.REFRESH_FAILED, "다시 로그인해 주세요.")
}
