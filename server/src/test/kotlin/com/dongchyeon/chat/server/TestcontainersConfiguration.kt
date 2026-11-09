package com.dongchyeon.chat.server

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

// ADR 0018: docker-compose.yml과 같은 이미지. @ServiceConnection이 접속 정보를 datasource에 넣어 준다
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun postgres() = PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
}
