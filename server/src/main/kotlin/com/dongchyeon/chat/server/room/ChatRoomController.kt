package com.dongchyeon.chat.server.room

import com.dongchyeon.chat.shared.ApiPaths
import com.dongchyeon.chat.shared.CursorPage
import com.dongchyeon.chat.shared.room.CreateDirectRoomRequest
import com.dongchyeon.chat.shared.room.CreateGroupRoomRequest
import com.dongchyeon.chat.shared.room.RoomMemberResponse
import com.dongchyeon.chat.shared.room.RoomResponse
import com.dongchyeon.chat.shared.room.RoomRules
import com.dongchyeon.chat.shared.room.RoomSummaryResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
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
        return ResponseEntity.status(status).body(result.room.withActiveMembers())
    }

    @PostMapping(ApiPaths.GROUP_ROOMS)
    @ResponseStatus(HttpStatus.CREATED)
    fun createGroup(
        @AuthenticationPrincipal userId: Long,
        @RequestBody request: CreateGroupRoomRequest,
    ): RoomResponse = chatRoomService.createGroup(userId, request.name).withActiveMembers()

    @PostMapping(ApiPaths.ROOM_JOIN)
    fun join(
        @AuthenticationPrincipal userId: Long,
        @PathVariable roomId: Long,
    ): RoomResponse = chatRoomService.join(userId, roomId).withActiveMembers()

    // 첫 페이지는 cursor를 생략한다 (ADR 0024)
    @GetMapping(ApiPaths.MY_ROOMS)
    fun myRooms(
        @AuthenticationPrincipal userId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) size: Int?,
    ): CursorPage<RoomSummaryResponse> = chatRoomService.myRooms(userId, cursor, size ?: RoomRules.PAGE_SIZE_DEFAULT)

    @PostMapping(ApiPaths.ROOM_LEAVE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leave(
        @AuthenticationPrincipal userId: Long,
        @PathVariable roomId: Long,
    ) = chatRoomService.leave(userId, roomId)

    // ADR 0028: 참여자는 ChatRoom.members(나간 사람 포함)가 아니라 참여 중인 사람만 조회해서 담는다
    private fun ChatRoom.withActiveMembers(): RoomResponse =
        toResponse(chatRoomService.activeMembers(checkNotNull(id)))
}

fun ChatRoom.toResponse(members: List<ChatRoomMember>) = RoomResponse(
    id = checkNotNull(id),
    type = type,
    name = name,
    members = members.map { RoomMemberResponse(userId = checkNotNull(it.user.id), nickname = it.user.nickname) },
)
