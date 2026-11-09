package com.dongchyeon.chat.server.user

import com.dongchyeon.chat.server.common.ApiException
import com.dongchyeon.chat.shared.ApiPaths
import com.dongchyeon.chat.shared.auth.UserResponse
import com.dongchyeon.chat.shared.error.ErrorCode
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

// 인증이 필요한 첫 API. 필터가 등록한 userId를 꺼내 쓴다
@RestController
class UserController(
    private val userRepository: UserRepository,
) {

    @GetMapping(ApiPaths.ME)
    fun me(@AuthenticationPrincipal userId: Long): UserResponse {
        val user = userRepository.findById(userId).orElseThrow {
            ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        }
        return UserResponse(id = checkNotNull(user.id), email = user.email, nickname = user.nickname)
    }
}
