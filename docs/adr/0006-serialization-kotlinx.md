# 0006. 직렬화: kotlinx.serialization

- 상태: 채택
- 날짜: 2026-11-07
- 로드맵 단계: 1
- Issue: #3

## 맥락

`shared` 모듈의 DTO를 서버(JVM), Android, iOS가 함께 쓴다. 지금 서버는 Spring 기본값인 Jackson을 쓰고 있다.

## 선택지

### A. Jackson
- 장점: Spring의 기본값이라 생태계 연동이 가장 넓다. 클래스에 어노테이션을 붙이지 않아도 된다.
- 단점: JVM 전용이라 iOS에서 쓸 수 없다. 런타임 리플렉션을 써서 모바일에서 무겁다.

### B. kotlinx.serialization
- 장점: Android, iOS, JVM에서 모두 동작한다. 컴파일 타임에 코드를 생성해 리플렉션이 없다. sealed class를 기본 지원한다.
- 단점: `@Serializable`과 컴파일러 플러그인이 필요하다. Spring 내부 기능 일부는 여전히 Jackson을 써서 둘이 공존할 수 있다.

## 결정

B. kotlinx.serialization을 사용한다.

## 이유 (오너 작성)

> KMP 특성상 Android, iOS, JVM에서 모두 동작하고 가볍기 때문에

## 결과

- `shared`의 DTO에 `@Serializable`을 붙이고, 서버의 HTTP 메시지 변환도 kotlinx.serialization으로 맞춘다.
- `shared`에서 날짜와 시간은 `java.time` 대신 `kotlinx-datetime`을 쓴다.

## 나중에 알게 된 것

