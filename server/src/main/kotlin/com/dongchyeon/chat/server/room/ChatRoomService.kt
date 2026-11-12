package com.dongchyeon.chat.server.room

import com.dongchyeon.chat.server.common.ApiException
import com.dongchyeon.chat.server.user.UserRepository
import com.dongchyeon.chat.shared.error.ErrorCode
import com.dongchyeon.chat.shared.room.RoomRules
import com.dongchyeon.chat.shared.room.RoomType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
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

    // 만든 사람이 첫 참여자가 된다. GROUP 방은 모두 공개 방이다 (ADR 0021)
    @Transactional
    fun createGroup(userId: Long, name: String): ChatRoom {
        if (!RoomRules.isValidName(name)) {
            throw ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ROOM_NAME, "방 이름은 1~${RoomRules.NAME_MAX_LENGTH}자여야 합니다.")
        }
        val user = findUser(userId)
        val room = chatRoomRepository.save(ChatRoom(type = RoomType.GROUP, name = name))
        chatRoomMemberRepository.save(room.addMember(user))
        return room
    }

    @Transactional
    fun join(userId: Long, roomId: Long): ChatRoom {
        val room = chatRoomRepository.findByIdOrNull(roomId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "방을 찾지 못했습니다.")
        if (room.type == RoomType.DIRECT) {
            throw ApiException(HttpStatus.FORBIDDEN, ErrorCode.DIRECT_ROOM_NOT_JOINABLE, "1:1 방에는 참여할 수 없습니다.")
        }
        // 1차 방어: 대부분의 중복 참여는 여기서 걸러진다
        if (chatRoomMemberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw alreadyJoined()
        }
        val user = findUser(userId)
        // 2차 방어: 동시 요청이 둘 다 위 확인을 통과하면 (user_id, room_id) unique 제약이 거른다.
        // 예외 뒤에 쿼리를 더 실행하지 않는다. PostgreSQL은 실패한 트랜잭션의 다음 쿼리를 거부한다
        try {
            chatRoomMemberRepository.save(room.addMember(user))
        } catch (e: DataIntegrityViolationException) {
            throw alreadyJoined()
        }
        return room
    }

    private fun findUser(userId: Long) = userRepository.findByIdOrNull(userId)
        ?: throw ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "유저를 찾지 못했습니다.")

    private fun alreadyJoined() = ApiException(HttpStatus.CONFLICT, ErrorCode.ALREADY_JOINED, "이미 참여한 방입니다.")
}

data class DirectRoomResult(val room: ChatRoom, val created: Boolean)
