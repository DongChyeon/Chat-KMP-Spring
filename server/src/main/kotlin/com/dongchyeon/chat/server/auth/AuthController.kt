package com.dongchyeon.chat.server.auth

import com.dongchyeon.chat.shared.ApiPaths
import com.dongchyeon.chat.shared.auth.LoginRequest
import com.dongchyeon.chat.shared.auth.LogoutRequest
import com.dongchyeon.chat.shared.auth.RefreshRequest
import com.dongchyeon.chat.shared.auth.SignupRequest
import com.dongchyeon.chat.shared.auth.TokenResponse
import com.dongchyeon.chat.shared.auth.UserResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val signupService: SignupService,
    private val loginService: LoginService,
    private val refreshService: RefreshService,
    private val logoutService: LogoutService,
) {

    @PostMapping(ApiPaths.SIGNUP)
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(@RequestBody request: SignupRequest): UserResponse {
        val user = signupService.signup(request)
        return UserResponse(id = checkNotNull(user.id), email = user.email, nickname = user.nickname)
    }

    @PostMapping(ApiPaths.LOGIN)
    fun login(@RequestBody request: LoginRequest): TokenResponse = loginService.login(request)

    @PostMapping(ApiPaths.REFRESH)
    fun refresh(@RequestBody request: RefreshRequest): TokenResponse = refreshService.refresh(request)

    @PostMapping(ApiPaths.LOGOUT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@AuthenticationPrincipal userId: Long, @RequestBody request: LogoutRequest) =
        logoutService.logout(userId, request.deviceId)
}
