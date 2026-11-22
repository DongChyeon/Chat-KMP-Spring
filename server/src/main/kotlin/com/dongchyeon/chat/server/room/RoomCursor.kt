package com.dongchyeon.chat.server.room

import com.dongchyeon.chat.server.common.ApiException
import com.dongchyeon.chat.shared.error.ErrorCode
import org.springframework.http.HttpStatus
import java.util.Base64

// ADR 0024: 앱은 커서를 해석하지 않고 그대로 돌려보낸다.
// 지금 정렬값은 방 id 하나라서(ADR 0023) id만 담는다. 3단계에서 (마지막 메시지 시각, id)로 바꿀 때 VERSION을 올린다
object RoomCursor {
    private const val VERSION = "v1"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(roomId: Long): String = encoder.encodeToString("$VERSION:$roomId".toByteArray())

    fun decode(cursor: String): Long {
        val text = runCatching { String(decoder.decode(cursor)) }.getOrNull()
        val roomId = text
            ?.takeIf { it.startsWith("$VERSION:") }
            ?.removePrefix("$VERSION:")
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
        return roomId ?: throw ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_CURSOR, "잘못된 커서입니다.")
    }
}
