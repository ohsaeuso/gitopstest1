# app

Spring Boot REST API with Keycloak OAuth2 integration.

## Stack

| 항목 | 버전 |
|------|------|
| Language | Kotlin 2.2.0 (JVM 21) |
| Framework | Spring Boot 3.2.5 |
| Security | Spring Security 6 + OAuth2 Client (Keycloak) |
| Database | Oracle 23 |

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/hello` | Public | Health check |
| GET | `/users/{username}/departments` | Public | 사용자 부서 조회 |
| GET | `/users/{username}/groups` | Public | 사용자 그룹 조회 |
| GET | `/home` | 인증 필요 | Keycloak access token 반환 |

## 실행

```bash
# 로컬 실행
./gradlew :app:bootRun

# JAR 빌드 (app/build/libs/app.jar)
./gradlew :app:bootJar
```

## OAuth2 / Keycloak 설정

`src/main/resources/application.yaml`에서 Keycloak 연동을 구성합니다.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: client1
            client-secret: <secret>
            scope: openid,profile,email
            redirect-uri: "http://worker1:30089/login/oauth2/code/keycloak"
        provider:
          keycloak:
            issuer-uri: https://keycloak.external.com/realms/realm1
```

## Testing

```bash
# 단위 테스트
./gradlew :app:test

# 통합 테스트 (Testcontainers — Docker 필요)
./gradlew :app:integrationTest
```

통합 테스트는 Oracle 23 컨테이너(`gvenzl/oracle-free:23-slim-faststart`)를 자동으로 띄웁니다.