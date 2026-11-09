package com.dongchyeon.chat.server.auth

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LogoutService(
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun logout(userId: Long, deviceId: String) {
        val deleted = refreshTokenRepository.deleteByUserIdAndDeviceId(userId, deviceId)
        log.info("logout: userId={}, deviceId={}, deleted={}", userId, deviceId, deleted)
    }
}
