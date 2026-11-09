package com.dongchyeon.chat.server.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByTokenHash(tokenHash: String): RefreshToken?
    fun findByUserIdAndDeviceId(userId: Long, deviceId: String): RefreshToken?
    // ADR 0017: 엔티티를 불러오지 않고 한 문장으로 지운다. 동시 요청이면 늦은 쪽은 0행 삭제로 끝난다
    @Modifying
    @Query("delete from RefreshToken r where r.user.id = :userId and r.deviceId = :deviceId")
    fun deleteByUserIdAndDeviceId(userId: Long, deviceId: String): Long
}
