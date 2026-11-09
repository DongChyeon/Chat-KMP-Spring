package com.dongchyeon.chat.shared.error

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val code: ErrorCode,
    val message: String,
)
