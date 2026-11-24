package com.dongchyeon.chat.shared.message

object MessageRules {
    const val CONTENT_MAX_LENGTH = 1000 // messages.content varchar(1000) (ADR 0038)
    const val PAGE_SIZE_DEFAULT = 30
    const val PAGE_SIZE_MAX = 100

    fun isValidContent(content: String): Boolean = content.isNotBlank() && content.length <= CONTENT_MAX_LENGTH
}
