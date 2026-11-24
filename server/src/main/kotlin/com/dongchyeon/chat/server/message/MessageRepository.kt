package com.dongchyeon.chat.server.message

import org.springframework.data.jpa.repository.JpaRepository

interface MessageRepository : JpaRepository<Message, Long>
