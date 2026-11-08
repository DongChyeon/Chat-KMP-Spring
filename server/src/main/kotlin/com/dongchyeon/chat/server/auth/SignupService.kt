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

@Service
class SignupService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {

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

        // TODO(learn): 2차 방어 — 동시 가입 요청 두 개가 위의 existsByEmail 확인을 둘 다 통과하면,
        //  아래 save()에서 DB의 UNIQUE 제약 위반이 난다. 지금은 이 예외가 그대로 올라가 500(INTERNAL_ERROR)으로 응답된다.
        //  이 경우에도 409(DUPLICATE_EMAIL)로 응답하도록 바꿔 보자.
        //  힌트 1: 어떤 예외가 올라오는지 먼저 확인한다 (DataIntegrityViolationException)
        //  힌트 2: save()를 호출하는 순간 INSERT가 바로 나갈까, 트랜잭션이 끝날 때 나갈까? ID 생성 전략(IDENTITY)과 관련 있다
        return userRepository.save(user)
    }
}
