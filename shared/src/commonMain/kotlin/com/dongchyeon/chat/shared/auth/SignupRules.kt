package com.dongchyeon.chat.shared.auth

import com.dongchyeon.chat.shared.error.ErrorCode

// 서버와 앱이 같은 규칙으로 검증한다. 앱은 요청을 보내기 전에, 서버는 받은 뒤에 쓴다.
object SignupRules {
    const val EMAIL_MAX_LENGTH = 255
    const val PASSWORD_MIN_LENGTH = 8
    const val PASSWORD_MAX_BYTES = 72 // BCrypt는 72바이트를 넘는 입력의 뒷부분을 무시한다 (ADR 0007)
    const val NICKNAME_MAX_LENGTH = 30

    private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun validate(request: SignupRequest): ErrorCode? = when {
        request.email.length > EMAIL_MAX_LENGTH || !EMAIL_PATTERN.matches(request.email) ->
            ErrorCode.INVALID_EMAIL

        // 글자 수가 아니라 바이트 수로 센다. 한글 한 글자는 UTF-8로 3바이트다.
        request.password.length < PASSWORD_MIN_LENGTH ||
            request.password.encodeToByteArray().size > PASSWORD_MAX_BYTES ->
            ErrorCode.INVALID_PASSWORD

        request.nickname.isBlank() || request.nickname.length > NICKNAME_MAX_LENGTH ->
            ErrorCode.INVALID_NICKNAME

        else -> null
    }
}
