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
import java.util.UUID

@Service
class LoginService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
) {
    private val dummyHash = checkNotNull(passwordEncoder.encode(UUID.randomUUID().toString()))

    @Transactional
    fun login(request: LoginRequest): TokenResponse {
        if (request.deviceId.isBlank()) {
            throw ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "잘못된 요청입니다.")
        }

        val user = userRepository.findByEmail(request.email)

        // 이메일이 없더라도 BCrypt 연산을 수행하여 비밀번호가 틀렸을 때와 실행 시간을 비슷하게 한다
        if (user == null) {
            passwordEncoder.matches(request.password, dummyHash)
            throw invalidCredentials()
        }
        if (!passwordEncoder.matches(request.password, user.password)) {
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
