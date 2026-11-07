# Chat-KMP-Spring

Spring Boot 서버와 Kotlin Multiplatform(Android/iOS) 앱으로 만드는 실시간 채팅 서비스입니다.
백엔드를 직접 구축하면서 학습하는 것이 목적입니다.

## 구조

```
server/       Spring Boot 4 (Kotlin, JDK 21)
shared/       서버와 앱이 함께 쓰는 DTO, API 경로
composeApp/   Compose Multiplatform UI
androidApp/   Android 진입점
iosApp/       iOS 진입점 (Xcode)
docs/adr/     설계 결정 기록
docs/learning/ 장애 실험 기록
```

## 실행

```bash
docker compose up -d --wait     # PostgreSQL, Redis
./gradlew :server:bootRun       # http://localhost:8080/api/info
```

Android 앱은 Android Studio에서 `androidApp`을 실행하고, iOS 앱은 Xcode에서 `iosApp/iosApp.xcodeproj`를 엽니다.
