package com.dongchyeon.chat.server.auth

import com.dongchyeon.chat.server.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

// ADR 0010: 유효한 Refresh Token만 저장하는 허용 목록. 기기마다 한 행.
@Entity
@Table(
    name = "refresh_tokens",
    // 같은 기기에서 다시 로그인하면 새 행을 만들지 않고 기존 행을 갱신한다
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "device_id"])],
)
class RefreshToken(
    // @ManyToOne의 기본값은 EAGER(즉시 로딩)다. LAZY로 바꿔 필요할 때만 사용자를 조회한다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "device_id", nullable = false, length = 100)
    val deviceId: String,

    // 원문 대신 해시를 저장한다. 재발급 요청 때 이 값으로 조회하므로 unique(인덱스)를 건다.
    @Column(nullable = false, unique = true, length = 64)
    var tokenHash: String,

    @Column(nullable = false)
    var expiresAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set
}
