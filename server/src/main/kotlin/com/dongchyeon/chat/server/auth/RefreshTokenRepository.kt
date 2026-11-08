package com.dongchyeon.chat.server.auth

import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByTokenHash(tokenHash: String): RefreshToken?
    fun findByUserIdAndDeviceId(userId: Long, deviceId: String): RefreshToken?
}
