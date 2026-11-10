package com.dongchyeon.chat.server.room

import com.dongchyeon.chat.server.common.ApiException
import com.dongchyeon.chat.server.user.UserRepository
import com.dongchyeon.chat.shared.error.ErrorCode
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

        // TODO(learn): ADR 0022 순서대로 구현한다
        //   ① 두 사용자 행을 id 오름차순으로 잠근다. 둘 중 없는 사용자가 있으면 404 NOT_FOUND
        //   ② chatRoomMemberRepository.findSharedRoom(…, RoomType.DIRECT)으로 기존 방을 찾고, 있으면 created = false로 돌려준다
        //   ③ 없으면 DIRECT 방을 저장하고 두 사람을 참여시킨다(ChatRoom.addMember)
        // 힌트: 잠금 메서드는 UserRepository에 직접 선언한다 — @Lock(LockModeType.PESSIMISTIC_WRITE), @Query의 order by
        TODO("learn")
    }
}

data class DirectRoomResult(val room: ChatRoom, val created: Boolean)
