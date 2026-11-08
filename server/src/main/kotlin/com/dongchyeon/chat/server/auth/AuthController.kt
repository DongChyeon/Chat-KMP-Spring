package com.dongchyeon.chat.server.auth

import com.dongchyeon.chat.shared.ApiPaths
import com.dongchyeon.chat.shared.auth.SignupRequest
import com.dongchyeon.chat.shared.auth.UserResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val signupService: SignupService,
) {

    @PostMapping(ApiPaths.SIGNUP)
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(@RequestBody request: SignupRequest): UserResponse {
        val user = signupService.signup(request)
        return UserResponse(id = checkNotNull(user.id), email = user.email, nickname = user.nickname)
    }
}
