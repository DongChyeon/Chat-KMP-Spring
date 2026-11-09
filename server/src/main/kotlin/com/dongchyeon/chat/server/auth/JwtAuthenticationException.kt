package com.dongchyeon.chat.server.auth

import com.dongchyeon.chat.shared.error.ErrorCode
import org.springframework.security.core.AuthenticationException

// 필터가 EntryPoint에 실패 이유(ErrorCode)를 넘기기 위한 예외 (ADR 0013)
class JwtAuthenticationException : AuthenticationException {
    val code: ErrorCode

    constructor(code: ErrorCode, message: String) : super(message) {
        this.code = code
    }

    constructor(code: ErrorCode, message: String, cause: Throwable) : super(message, cause) {
        this.code = code
    }
}
