package com.dongchyeon.chat.server.room

import com.dongchyeon.chat.server.common.ApiException
import com.dongchyeon.chat.server.user.UserRepository
import com.dongchyeon.chat.shared.error.ErrorCode
import com.dongchyeon.chat.shared.room.RoomType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChatRoomService(
    private val userRepository: UserRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
) {

    // ADR 0022: 이미 방이 있으면 그 방을 돌려준다(created = false)
    @Transactional
    fun createDirect(userId: Long, targetUserId: Long): DirectRoomResult {
        if (userId == targetUserId) {
            throw ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "자기 자신과는 1:1 방을 만들 수 없습니다.")
        }

        val users = userRepository.findAllByIdForUpdate(listOf(userId, targetUserId))
        if (users.size < 2) {
            throw ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "유저를 찾지 못했습니다.")
        }

        val room = chatRoomMemberRepository.findSharedRoom(userId, targetUserId, RoomType.DIRECT)

        if (room != null) {
            return DirectRoomResult(room, false)
        } else {
            val chatRoom = ChatRoom(type = RoomType.DIRECT, name = null)
            chatRoomRepository.save(chatRoom)
            for (user in users) chatRoomMemberRepository.save(chatRoom.addMember(user))

            return DirectRoomResult(chatRoom, true)
        }
    }
}

data class DirectRoomResult(val room: ChatRoom, val created: Boolean)
