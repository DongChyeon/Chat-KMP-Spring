package com.dongchyeon.chat.shared.realtime

// 서버가 연결을 닫을 때 쓰는 close code. 4000~4999는 애플리케이션이 정의하는 구간이다 (RFC 6455)
object RealtimeCloseCode {
    // 직전에 보낸 ServerFrame.Error의 code로 원인을 구분한다. TOKEN_EXPIRED면 재발급 뒤 다시 연결한다
    const val AUTH_FAILED = 4001

    // 연결 후 정해진 시간 안에 AUTH 프레임이 오지 않았다 (ADR 0031)
    const val AUTH_TIMEOUT = 4002
}
