package com.dongchyeon.chat.server.room

import com.dongchyeon.chat.shared.ApiPaths
import com.dongchyeon.chat.shared.room.CreateDirectRoomRequest
import com.dongchyeon.chat.shared.room.RoomMemberResponse
import com.dongchyeon.chat.shared.room.RoomResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ChatRoomController(
    private val chatRoomService: ChatRoomService,
) {

    // 새로 만들면 201, 기존 방이면 200 (ADR 0022 추가 결정)
    @PostMapping(ApiPaths.DIRECT_ROOMS)
    fun createDirect(
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: CreateDirectRoomRequest,
    ): ResponseEntity<RoomResponse> {
        val result = chatRoomService.createDirect(userId, request.targetUserId)
        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(result.room.toResponse())
    }
}

fun ChatRoom.toResponse() = RoomResponse(
    id = checkNotNull(id),
    type = type,
    name = name,
    members = members.map { RoomMemberResponse(userId = checkNotNull(it.user.id), nickname = it.user.nickname) },
)
