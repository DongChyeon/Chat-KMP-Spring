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

    // 로그인: 이메일이 없는 경우와 비밀번호가 틀린 경우를 구분하지 않는다 (계정 열거 방지)
    INVALID_CREDENTIALS,

    // 인증 필터 (ADR 0013). 앱의 동작이 달라서 나눈다
    UNAUTHORIZED, // 보호된 경로에 토큰 없이 요청 → 로그인 화면으로
    TOKEN_EXPIRED, // Access Token 만료 → Refresh Token으로 재발급 시도
    INVALID_TOKEN, // 위조, 형식 오류, 종류가 다른 토큰 → 로그아웃

    // 토큰 재발급: 원인(만료, 폐기, 위조)을 나누지 않는다. 앱은 받으면 로그인 화면으로 (ADR 0014)
    REFRESH_FAILED,
}
