package com.dongchyeon.chat.server

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future

// 모든 작업이 출발 신호(gate)를 기다리다가 동시에 task를 실행한다
fun <T> runConcurrently(threads: Int, task: (index: Int) -> T): List<T> {
    val threadPool = Executors.newFixedThreadPool(threads)
    try {
        val gate = CountDownLatch(1)

        val futures: List<Future<T>> = (0 until threads).map { index ->
            threadPool.submit<T> {
                gate.await()
                task(index)
            }
        }
        gate.countDown()
        return futures.map { it.get() }
    } finally {
        threadPool.shutdown()
    }
}
