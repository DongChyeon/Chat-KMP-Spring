package com.dongchyeon.chat.server.realtime

import com.dongchyeon.chat.server.TestcontainersConfiguration
import com.dongchyeon.chat.server.auth.JwtProperties
import com.dongchyeon.chat.server.auth.JwtProvider
import com.dongchyeon.chat.server.auth.TokenType
import com.dongchyeon.chat.server.room.RoomTestClient
import com.dongchyeon.chat.shared.error.ErrorCode
import com.dongchyeon.chat.shared.realtime.ClientFrame
import com.dongchyeon.chat.shared.realtime.RealtimeCloseCode
import com.dongchyeon.chat.shared.realtime.ServerFrame
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import java.time.Duration
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals

// ADR 0031, 0033: 연결 후 첫 프레임 AUTH로 인증한다
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class RealtimeAuthTest(
    @LocalServerPort private val port: Int,
    @Autowired private val jwtProvider: JwtProvider,
    @Autowired private val jwtProperties: JwtProperties,
) {
    private val api = RoomTestClient(port)
    private val clients = mutableListOf<RealtimeTestClient>()

    @AfterEach
    fun closeAll() = clients.forEach { it.close() }

    private fun connect() = RealtimeTestClient.connect(port).also { clients.add(it) }

    @Test
    fun `유효한 Access Token으로 AUTH하면 AUTH_OK를 받는다`() {
        val user = api.newUser()
        val client = connect()

        client.send(ClientFrame.Auth(user.accessToken))

        assertEquals(ServerFrame.AuthOk(user.id), client.receive())
    }

    @Test
    fun `AUTH 전에 다른 프레임을 보내면 UNAUTHORIZED를 받고 끊긴다`() {
        val client = connect()

        client.send(ClientFrame.Send(requestId = "r1", roomId = 1, content = "hi"))

        assertEquals(ServerFrame.Error(ErrorCode.UNAUTHORIZED), client.receive())
        assertEquals(RealtimeCloseCode.AUTH_FAILED, client.awaitClose())
    }

    @Test
    fun `형식이 깨진 첫 프레임은 INVALID_REQUEST를 받고 끊긴다`() {
        val client = connect()

        client.sendRaw("""{"type":"HELLO"}""")

        assertEquals(ServerFrame.Error(ErrorCode.INVALID_REQUEST), client.receive())
        assertEquals(RealtimeCloseCode.AUTH_FAILED, client.awaitClose())
    }

    @Test
    fun `만료된 토큰은 TOKEN_EXPIRED를 받고 끊긴다`() {
        val user = api.newUser()
        val expired = jwtProvider.createAccessToken(user.id, "phone", now = Instant.now().minus(Duration.ofHours(1)))
        val client = connect()

        client.send(ClientFrame.Auth(expired))

        assertEquals(ServerFrame.Error(ErrorCode.TOKEN_EXPIRED), client.receive())
        assertEquals(RealtimeCloseCode.AUTH_FAILED, client.awaitClose())
    }

    @Test
    fun `Refresh Token으로 AUTH하면 INVALID_TOKEN을 받고 끊긴다`() {
        val user = api.newUser()
        val refresh = jwtProvider.createRefreshToken(user.id).value
        val client = connect()

        client.send(ClientFrame.Auth(refresh))

        assertEquals(ServerFrame.Error(ErrorCode.INVALID_TOKEN), client.receive())
        assertEquals(RealtimeCloseCode.AUTH_FAILED, client.awaitClose())
    }

    @Test
    fun `deviceId가 없는 예전 Access Token은 INVALID_TOKEN을 받고 끊긴다`() {
        val user = api.newUser()
        val now = Instant.now()
        val legacy = Jwts.builder()
            .subject(user.id.toString())
            .claim(JwtProvider.TYPE_CLAIM, TokenType.ACCESS.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(Duration.ofMinutes(15))))
            .signWith(Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray()), Jwts.SIG.HS256)
            .compact()
        val client = connect()

        client.send(ClientFrame.Auth(legacy))

        assertEquals(ServerFrame.Error(ErrorCode.INVALID_TOKEN), client.receive())
        assertEquals(RealtimeCloseCode.AUTH_FAILED, client.awaitClose())
    }

    @Test
    fun `로그인과 재발급으로 받은 Access Token에는 같은 deviceId가 들어 있다`() {
        val (loginToken, refreshedToken) = api.loginAndRefresh(deviceId = "tablet-1")

        assertEquals("tablet-1", jwtProvider.parse(loginToken)[JwtProvider.DEVICE_ID_CLAIM])
        assertEquals("tablet-1", jwtProvider.parse(refreshedToken)[JwtProvider.DEVICE_ID_CLAIM])
    }
}
