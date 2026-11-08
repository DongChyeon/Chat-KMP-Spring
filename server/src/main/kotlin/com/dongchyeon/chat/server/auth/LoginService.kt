package com.dongchyeon.chat.server.auth

import com.dongchyeon.chat.server.common.ApiException
import com.dongchyeon.chat.server.user.UserRepository
import com.dongchyeon.chat.shared.auth.LoginRequest
import com.dongchyeon.chat.shared.auth.TokenResponse
import com.dongchyeon.chat.shared.error.ErrorCode
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LoginService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
) {

    @Transactional
    fun login(request: LoginRequest): TokenResponse {
        if (request.deviceId.isBlank()) {
            throw ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "잘못된 요청입니다.")
        }

        val user = userRepository.findByEmail(request.email)

        // TODO(learn): 타이밍 공격 — 에러 메시지는 하나로 통일했지만 응답 시간으로 계정 존재 여부가 샌다.
        //  이메일이 없으면 BCrypt 비교 없이 바로 실패해서 빠르고, 있으면 BCrypt 비교를 하느라 느리다.
        //  ./scripts/login-timing.sh 로 두 경우의 평균 응답 시간을 재 보고, 차이가 없어지도록 고쳐 보자.
        //  힌트: 이메일이 없을 때도 BCrypt 비교를 한 번 하게 만들 수 있을까? 무엇과 비교하면 될까?
        if (user == null || !passwordEncoder.matches(request.password, user.password)) {
            throw invalidCredentials()
        }

        val userId = checkNotNull(user.id)
        val accessToken = jwtProvider.createAccessToken(userId)
        val refreshToken = jwtProvider.createRefreshToken(userId)

        // 같은 기기에서 다시 로그인하면 기존 행을 갱신하고, 새 기기면 행을 추가한다 (ADR 0010: 기기별 저장)
        val saved = refreshTokenRepository.findByUserIdAndDeviceId(userId, request.deviceId)
        if (saved != null) {
            saved.tokenHash = TokenHasher.hash(refreshToken.value)
            saved.expiresAt = refreshToken.expiresAt
        } else {
            refreshTokenRepository.save(
                RefreshToken(
                    user = user,
                    deviceId = request.deviceId,
                    tokenHash = TokenHasher.hash(refreshToken.value),
                    expiresAt = refreshToken.expiresAt,
                ),
            )
        }

        return TokenResponse(accessToken = accessToken, refreshToken = refreshToken.value)
    }

    private fun invalidCredentials() =
        ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "이메일 또는 비밀번호가 올바르지 않습니다.")
}
