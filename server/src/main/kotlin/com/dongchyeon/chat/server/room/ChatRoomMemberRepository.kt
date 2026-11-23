package com.dongchyeon.chat.server.room

import com.dongchyeon.chat.shared.room.RoomType
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
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

    fun findByRoomIdAndUserId(roomId: Long, userId: Long): ChatRoomMember?

    // ADR 0042: 재참여와 퇴장이 정확히 한 번만 성공하도록 참여 행을 잠그고 읽는다
    // TODO(learn): 이 조회가 다른 트랜잭션이 커밋할 때까지 기다리게 만들기 — 힌트: UserRepository.findAllByIdForUpdate (ADR 0022)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ChatRoomMember m where m.room.id = :roomId and m.user.id = :userId")
    fun findByRoomIdAndUserIdForUpdate(roomId: Long, userId: Long): ChatRoomMember?

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

    // 내 채팅방 목록 한 페이지 (ADR 0023, 0024). 첫 페이지는 cursorId에 Long.MAX_VALUE가 들어온다.
    // 정렬 기준과 커서 기준은 같은 컬럼(room_id)이어야 페이지 사이에 빠지거나 겹치는 방이 없다.
    // 나간 방은 빼고(ADR 0026), 개수는 limit 파라미터로 Spring Data가 붙인다(fetch first ? rows only)
    @Query(
        """
        select m.room from ChatRoomMember m
        where m.user.id = :userId and m.room.id < :cursorId and m.leftAt is null
        order by m.room.id desc
        """,
    )
    fun findMyRooms(userId: Long, cursorId: Long, limit: Limit): List<ChatRoom>

    // 1:1 방들의 상대 닉네임을 한 번에 (ADR 0029).
    // 상대가 나갔어도 그 방은 여전히 "그 사람과의 방"이므로 left_at은 보지 않는다
    @Query(
        """
        select new com.dongchyeon.chat.server.room.DirectPartner(m.room.id, u.nickname)
        from ChatRoomMember m join m.user u
        where m.room.id in :roomIds and m.user.id <> :userId
        """,
    )
    fun findDirectPartners(roomIds: Collection<Long>, userId: Long): List<DirectPartner>
}
