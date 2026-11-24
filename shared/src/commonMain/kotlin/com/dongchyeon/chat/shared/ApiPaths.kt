package com.dongchyeon.chat.shared

object ApiPaths {
    const val INFO = "/api/info"
    const val SIGNUP = "/api/auth/signup"
    const val LOGIN = "/api/auth/login"
    const val REFRESH = "/api/auth/refresh"
    const val LOGOUT = "/api/auth/logout"
    const val ME = "/api/users/me"

    const val MY_ROOMS = "/api/rooms" // GET ?cursor=&size=
    const val PUBLIC_ROOMS = "/api/rooms/public" // GET ?cursor=&size=
    const val DIRECT_ROOMS = "/api/rooms/direct" // POST
    const val GROUP_ROOMS = "/api/rooms/group" // POST
    const val ROOM_JOIN = "/api/rooms/{roomId}/join" // POST
    const val ROOM_LEAVE = "/api/rooms/{roomId}/leave" // POST
    // GET ?cursor=&size= 최신순 이전 페이지, ?after=&size= 재연결 이어받기 오름차순 (ADR 0039)
    const val ROOM_MESSAGES = "/api/rooms/{roomId}/messages"

    const val REALTIME = "/ws" // WebSocket 엔드포인트 (ADR 0030)

    fun roomJoin(roomId: Long) = ROOM_JOIN.replace("{roomId}", roomId.toString())
    fun roomLeave(roomId: Long) = ROOM_LEAVE.replace("{roomId}", roomId.toString())
    fun roomMessages(roomId: Long) = ROOM_MESSAGES.replace("{roomId}", roomId.toString())
}
