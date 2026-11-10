package com.dongchyeon.chat.server.room

import com.dongchyeon.chat.server.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

// ADR 0020: 사용자와 채팅방의 N:M을 푸는 중간 엔티티
@Entity
@Table(
    name = "chat_room_members",
    // ADR 0025: unique가 내 방 목록(user_id로 찾고 room_id 순서)을 겸하고, room_id 인덱스가 참여자 조회를 맡는다
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "room_id"])],
    indexes = [Index(name = "idx_chat_room_members_room_id", columnList = "room_id")],
)
class ChatRoomMember(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    val room: ChatRoom,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    val joinedAt: Instant = Instant.now()
}
