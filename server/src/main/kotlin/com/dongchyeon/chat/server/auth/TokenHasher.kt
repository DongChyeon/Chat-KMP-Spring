package com.dongchyeon.chat.server.auth

import java.security.MessageDigest

// Refresh Token은 원문 대신 SHA-256 해시로 저장한다 (ADR 0010).
// 비밀번호와 달리 BCrypt를 쓰지 않는 이유: 토큰은 사람이 정한 값이 아니라 충분히 긴 무작위 값이라
// 대입 공격을 늦출 필요가 없고, 해시로 DB를 바로 조회해야 하므로 매번 같은 값이 나와야 한다.
object TokenHasher {
    fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
