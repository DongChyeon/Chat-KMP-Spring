package com.dongchyeon.chat.server.realtime

import com.dongchyeon.chat.shared.ApiPaths
import com.dongchyeon.chat.shared.realtime.ClientFrame
import com.dongchyeon.chat.shared.realtime.ServerFrame
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.fail

// JDK 내장 WebSocket 클라이언트. 앱의 OkHttp WebSocketListener와 같은 역할이다
class RealtimeTestClient private constructor(private val socket: WebSocket, private val listener: Listener) {

    val closeCode: CompletableFuture<Int> get() = listener.closeCode

    fun send(frame: ClientFrame) {
        socket.sendText(Json.encodeToString(frame), true).join()
    }

    fun sendRaw(text: String) {
        socket.sendText(text, true).join()
    }

    fun receive(timeout: Duration = Duration.ofSeconds(3)): ServerFrame {
        val text = listener.frames.poll(timeout.toMillis(), TimeUnit.MILLISECONDS) ?: fail("${timeout} 안에 프레임을 받지 못했다")
        return Json.decodeFromString(text)
    }

    fun awaitClose(timeout: Duration = Duration.ofSeconds(3)): Int =
        closeCode.get(timeout.toMillis(), TimeUnit.MILLISECONDS)

    fun close() {
        if (!socket.isOutputClosed) socket.sendClose(WebSocket.NORMAL_CLOSURE, "").exceptionally { null }
        socket.abort()
    }

    private class Listener : WebSocket.Listener {
        val frames = LinkedBlockingQueue<String>()
        val closeCode = CompletableFuture<Int>()
        private val buffer = StringBuilder()

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                frames.add(buffer.toString())
                buffer.clear()
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            closeCode.complete(statusCode)
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            closeCode.completeExceptionally(error)
        }
    }

    companion object {
        fun connect(port: Int, timeout: Duration = Duration.ofSeconds(3)): RealtimeTestClient {
            val listener = Listener()
            val socket = HttpClient.newBuilder().connectTimeout(timeout).build()
                .newWebSocketBuilder()
                .connectTimeout(timeout)
                .buildAsync(URI.create("ws://localhost:$port${ApiPaths.REALTIME}"), listener)
                .get(timeout.toMillis() * 2, TimeUnit.MILLISECONDS)
            return RealtimeTestClient(socket, listener)
        }
    }
}
