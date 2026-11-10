package com.dongchyeon.chat.shared

import kotlinx.serialization.Serializable

// ADR 0024: 앱은 nextCursor를 해석하지 않고 그대로 다음 요청에 보낸다. null이면 마지막 페이지
@Serializable
data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String?,
)
