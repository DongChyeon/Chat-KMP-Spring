package com.dongchyeon.chat.server.room

import com.dongchyeon.chat.server.user.User
import com.dongchyeon.chat.shared.room.RoomType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "chat_rooms")
class ChatRoom(
    // 문자열로 저장한다. ORDINAL(순서 번호)은 enum 순서를 바꾸면 기존 데이터의 의미가 바뀐다
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val type: RoomType,

    @Column(length = 50)
    var name: String?, // GROUP 방만 쓴다
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    // 연관관계의 주인은 ChatRoomMember.room이다. 이 컬렉션은 읽기용이고, 참여는 ChatRoomMember를 저장해서 한다
    @OneToMany(mappedBy = "room")
    val members: MutableList<ChatRoomMember> = mutableListOf()

    // 참여 행을 만들면서 members에도 넣는다. 리포지토리로 저장만 하면 이 트랜잭션 안의 members는 비어 있다
    fun addMember(user: User): ChatRoomMember = ChatRoomMember(room = this, user = user).also { members.add(it) }
}
