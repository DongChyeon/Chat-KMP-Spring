package com.dongchyeon.chat.server.auth

import com.dongchyeon.chat.server.common.ApiException
import com.dongchyeon.chat.server.user.User
import com.dongchyeon.chat.server.user.UserRepository
import com.dongchyeon.chat.shared.auth.SignupRequest
import com.dongchyeon.chat.shared.auth.SignupRules
import com.dongchyeon.chat.shared.error.ErrorCode
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException

@Service
class SignupService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun signup(request: SignupRequest): User {
        SignupRules.validate(request)?.let { code ->
            throw ApiException(HttpStatus.BAD_REQUEST, code, "입력값을 확인해 주세요.")
        }

        // 1차 방어: 대부분의 중복 가입은 여기서 걸러진다
        if (userRepository.existsByEmail(request.email)) {
            throw ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_EMAIL, "이미 가입된 이메일입니다.")
        }

        val user = User(
            email = request.email,
            password = checkNotNull(passwordEncoder.encode(request.password)),
            nickname = request.nickname,
        )


        // 2차 방어: 동시 가입 요청 두 개가 위의 existsByEmail 확인을 둘 다 통과하는 경우를 거른다
        return try {
            userRepository.save(user)
        } catch (e: DataIntegrityViolationException) {
            throw ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_EMAIL, "이미 가입된 이메일입니다.")
        }
    }
}
