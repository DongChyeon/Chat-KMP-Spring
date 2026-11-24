package com.dongchyeon.chat.server.message

import com.dongchyeon.chat.server.room.ChatRoom
import com.dongchyeon.chat.server.user.User
import com.dongchyeon.chat.shared.message.MessageRules
import com.dongchyeon.chat.shared.message.MessageType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

// ADR 0038, 0041
@Entity
@Table(
    name = "messages",
    // 방의 최신 N개를 정렬 없이 역방향 인덱스 스캔으로 읽는다. 이어받기(after)는 같은 인덱스를 정방향으로 읽는다
    indexes = [Index(name = "idx_messages_room_id_id", columnList = "room_id, id")],
)
class Message private constructor(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    val room: ChatRoom,

    // 참여 행이 아니라 users를 참조한다. 나간 사람의 메시지도 남아야 한다
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    val sender: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val type: MessageType,

    // JOIN/LEAVE는 문구를 저장하지 않으므로 null (ADR 0041)
    @Column(length = MessageRules.CONTENT_MAX_LENGTH)
    val content: String?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    companion object {
        fun text(room: ChatRoom, sender: User, content: String) = Message(room, sender, MessageType.TEXT, content)
        fun join(room: ChatRoom, user: User) = Message(room, user, MessageType.JOIN, null)
        fun leave(room: ChatRoom, user: User) = Message(room, user, MessageType.LEAVE, null)
    }
}
