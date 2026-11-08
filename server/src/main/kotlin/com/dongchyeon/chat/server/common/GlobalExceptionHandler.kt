package com.dongchyeon.chat.server.common

import com.dongchyeon.chat.shared.error.ErrorCode
import com.dongchyeon.chat.shared.error.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

// ADR 0011: 모든 에러를 shared의 ErrorResponse 형식으로 맞춘다
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(ex.status).body(ErrorResponse(ex.code, ex.message))

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("처리되지 않은 예외", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(ErrorCode.INTERNAL_ERROR, "서버 오류가 발생했습니다."))
    }

    // Spring MVC가 만드는 기본 에러(잘못된 JSON, 없는 경로, 지원하지 않는 메서드 등)를 ErrorResponse로 바꾼다
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val code = when (statusCode.value()) {
            404 -> ErrorCode.NOT_FOUND
            405 -> ErrorCode.METHOD_NOT_ALLOWED
            in 400..499 -> ErrorCode.INVALID_REQUEST
            else -> ErrorCode.INTERNAL_ERROR
        }
        val message = if (statusCode.is4xxClientError) "잘못된 요청입니다." else "서버 오류가 발생했습니다."
        return ResponseEntity.status(statusCode).headers(headers).body(ErrorResponse(code, message))
    }
}
