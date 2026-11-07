package com.dongchyeon.chat.server

import com.dongchyeon.chat.shared.ApiPaths
import com.dongchyeon.chat.shared.ServerInfo
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

// shared 모듈의 경로 상수와 DTO를 서버에서 그대로 쓰는지 확인하는 엔드포인트
@RestController
class InfoController {

    @GetMapping(ApiPaths.INFO)
    fun info(): ServerInfo = ServerInfo(name = "chat-server", version = "0.0.1")
}
