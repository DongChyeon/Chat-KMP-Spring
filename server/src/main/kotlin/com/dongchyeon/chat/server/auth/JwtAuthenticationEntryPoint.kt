package com.dongchyeon.chat.server.auth

import com.dongchyeon.chat.shared.error.ErrorCode
import com.dongchyeon.chat.shared.error.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.serialization.json.Json
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

// ADR 0013: 401 응답을 쓰는 곳은 여기 하나다.
// 1) 필터가 잘못된 토큰을 발견하고 직접 호출 → JwtAuthenticationException(TOKEN_EXPIRED / INVALID_TOKEN)
// 2) 토큰 없이 보호된 경로에 접근 → AuthorizationFilter 거부 → ExceptionTranslationFilter가 호출 (UNAUTHORIZED)
// 필터는 DispatcherServlet 앞이라 GlobalExceptionHandler가 닿지 않으므로 응답을 직접 쓴다.
@Component
class JwtAuthenticationEntryPoint : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        val body = when (authException) {
            is JwtAuthenticationException -> ErrorResponse(authException.code, authException.message ?: "인증에 실패했습니다.")
            else -> ErrorResponse(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.")
        }
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(Json.encodeToString(ErrorResponse.serializer(), body))
    }
}
