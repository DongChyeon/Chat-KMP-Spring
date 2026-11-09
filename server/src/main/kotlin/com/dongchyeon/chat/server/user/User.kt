package com.dongchyeon.chat.server.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "users") // "user"는 PostgreSQL 예약어라 복수형을 쓴다
class User(
    // unique = true: 동시 가입 요청이 코드의 중복 확인을 통과해도 DB가 두 번째 저장을 거절한다
    @Column(nullable = false, unique = true)
    val email: String,

    // BCrypt 해시가 저장된다 (ADR 0007). 원문은 저장하지 않는다.
    @Column(nullable = false)
    var password: String,

    @Column(nullable = false, length = 30)
    var nickname: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
}
