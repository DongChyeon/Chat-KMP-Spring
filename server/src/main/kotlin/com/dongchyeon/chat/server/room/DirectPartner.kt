package com.dongchyeon.chat.server.room

// 1:1 방의 상대 닉네임 조회 결과 (ADR 0029). JPQL의 select new로 바로 만든다
data class DirectPartner(val roomId: Long, val nickname: String)
