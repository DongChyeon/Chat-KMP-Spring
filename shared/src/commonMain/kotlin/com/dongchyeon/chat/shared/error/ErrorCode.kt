package com.dongchyeon.chat.shared.error

import kotlinx.serialization.Serializable

// 서버와 앱이 함께 쓰는 에러 코드 (ADR 0011). 앱은 이 값으로 분기한다.
@Serializable
enum class ErrorCode {
    // 공통
    INVALID_REQUEST,
    NOT_FOUND,
    METHOD_NOT_ALLOWED,
    INTERNAL_ERROR,

    // 회원가입
    INVALID_EMAIL,
    INVALID_PASSWORD,
    INVALID_NICKNAME,
    DUPLICATE_EMAIL,
}
