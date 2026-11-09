package com.dongchyeon.chat.server.auth

import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByTokenHash(tokenHash: String): RefreshToken?
    fun findByUserIdAndDeviceId(userId: Long, deviceId: String): RefreshToken?
    // TODO(learn): 벌크 DELETE로 바꾸기 (ADR 0017). LogoutService의 호출부도 함께 맞춘다
    //  힌트: @Modifying, @Query(JPQL은 테이블이 아닌 엔티티·필드 이름을 쓴다), 반환 타입은 삭제된 행 수
    fun deleteByUserIdAndDeviceId(userId: Long, deviceId: String): Long
}
