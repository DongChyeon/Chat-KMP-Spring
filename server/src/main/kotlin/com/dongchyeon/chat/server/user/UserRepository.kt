package com.dongchyeon.chat.server.user

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean

    // ADR 0022: SELECT ... FOR UPDATE. 데드락을 막으려고 항상 id 오름차순으로 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id in :ids order by u.id")
    fun findAllByIdForUpdate(ids: Collection<Long>): List<User>
}
