package com.dongchyeon.chat.server.room

import com.dongchyeon.chat.shared.room.RoomType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ChatRoomMemberRepository : JpaRepository<ChatRoomMember, Long> {

    // 참여자 테이블을 자기 자신과 조인해 두 사람이 함께 속한 방을 찾는다 (ADR 0022)
    @Query(
        """
        select m1.room from ChatRoomMember m1
        join ChatRoomMember m2 on m2.room = m1.room
        where m1.user.id = :userId and m2.user.id = :otherUserId and m1.room.type = :type
        """,
    )
    fun findSharedRoom(userId: Long, otherUserId: Long, type: RoomType): ChatRoom?
}
