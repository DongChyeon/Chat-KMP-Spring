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

    fun findByRoomIdAndUserIdAndLeftAtIsNull(roomId: Long, userId: Long): ChatRoomMember?

    fun findByRoomIdAndUserId(roomId: Long, userId: Long): ChatRoomMember?

    // ADR 0028: 참여 중인 참여자만, 들어온 순서대로.
    // fetch join으로 사용자까지 이 쿼리에서 채운다. 일반 join이면 m.user가 프록시로 남아 참여자마다 users 조회가 나간다(N+1)
    @Query(
        """
        select m from ChatRoomMember m
        join fetch m.user
        where m.room.id = :roomId and m.leftAt is null
        order by m.id
        """,
    )
    fun findActiveMembers(roomId: Long): List<ChatRoomMember>
}
