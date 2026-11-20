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

    // ADR 0022: 이미 방이 있으면 그 방을 돌려준다(created = false). 나갔던 방이어도 같은 방을 돌려준다 (ADR 0026)
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
            // ADR 0026: 나갔던 쪽(요청한 사람, 상대, 또는 둘 다)의 참여 행을 되살린다.
            // 위에서 두 사용자 행을 잠갔으므로 같은 두 사람의 요청은 하나씩 처리된다 (ADR 0027과 달리 중복 전이가 없다)
            val roomId = checkNotNull(room.id) { "저장된 채팅방에 id가 없습니다" }

            // findSharedRoom은 두 사람의 참여 행을 조인해서 찾으므로 두 행은 반드시 있다. 없으면 데이터가 깨진 것이다
            val member = checkNotNull(chatRoomMemberRepository.findByRoomIdAndUserId(roomId, userId)) {
                "1:1 방에 요청한 사람의 참여 행이 없습니다: roomId=$roomId, userId=$userId"
            }
            val targetMember = checkNotNull(chatRoomMemberRepository.findByRoomIdAndUserId(roomId, targetUserId)) {
                "1:1 방에 상대의 참여 행이 없습니다: roomId=$roomId, userId=$targetUserId"
            }

            if (member.leftAt != null) member.rejoin()
            if (targetMember.leftAt != null) targetMember.rejoin()

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

        val member = chatRoomMemberRepository.findByRoomIdAndUserId(roomId, userId)
        if (member != null) {
            val isUserLeft = member.leftAt != null
            if (isUserLeft) {
                member.rejoin()
                return room
            } else {
                // 1차 방어: 대부분의 중복 참여는 여기서 걸러진다
                throw alreadyJoined()
            }
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

    // ADR 0026: 1:1 방, 그룹 방 모두 나갈 수 있다. 참여 행에 나간 시각만 남긴다
    @Transactional
    fun leave(userId: Long, roomId: Long) {
        val member = chatRoomMemberRepository.findByRoomIdAndUserIdAndLeftAtIsNull(roomId, userId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "참여 중인 방이 아닙니다.")
        member.leave()
    }

    // ADR 0028: 응답용 참여자 목록. ChatRoom.members는 나간 사람까지 담으므로 쓰지 않는다
    @Transactional(readOnly = true)
    fun activeMembers(roomId: Long): List<ChatRoomMember> = chatRoomMemberRepository.findActiveMembers(roomId)

    private fun findUser(userId: Long) = userRepository.findByIdOrNull(userId)
        ?: throw ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "유저를 찾지 못했습니다.")

    private fun alreadyJoined() = ApiException(HttpStatus.CONFLICT, ErrorCode.ALREADY_JOINED, "이미 참여한 방입니다.")
}

data class DirectRoomResult(val room: ChatRoom, val created: Boolean)
