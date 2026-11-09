package com.dongchyeon.chat.server.auth

import com.dongchyeon.chat.server.common.ApiException
import com.dongchyeon.chat.shared.auth.RefreshRequest
import com.dongchyeon.chat.shared.auth.TokenResponse
import com.dongchyeon.chat.shared.error.ErrorCode
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
        // TODO(learn): Refresh Token 검증 3단계를 작성하기
        //  1) 서명·만료 검증  2) type이 REFRESH인지  3) 허용 목록(refresh_tokens)에 있는지
        //  힌트: JwtAuthenticationFilter.authenticate()와 무엇이 같고 무엇이 다른가, TokenHasher, findByTokenHash
        //  힌트: 각 실패마다 log.info로 원인을 남긴다. userId는 JWT subject와 DB 행 중 어디서 가져와도 되는가?
        log.info("refresh failed: not implemented")
        throw refreshFailed()
    }

    private fun refreshFailed() =
        ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.REFRESH_FAILED, "다시 로그인해 주세요.")
}
