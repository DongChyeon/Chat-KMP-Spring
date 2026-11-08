package com.dongchyeon.chat.server.common

import com.dongchyeon.chat.shared.error.ErrorCode
import org.springframework.http.HttpStatus

class ApiException(
    val status: HttpStatus,
    val code: ErrorCode,
    override val message: String,
) : RuntimeException(message)
