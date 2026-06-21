# Local Dev Setup

## Windows + Docker Desktop (WSL2)

Testcontainers는 기본적으로 `\\.\pipe\docker_engine`에 연결을 시도하지만, Docker Desktop 최신 버전은 이 파이프를 실제 엔진으로 연결하지 않고 400 응답을 반환한다.

Docker Desktop 레이블(`com.docker.desktop.address`)에 명시된 실제 파이프는 `\\.\pipe\docker_cli`이므로, 아래 환경변수를 Windows 사용자 환경변수로 설정해야 한다.

```powershell
[System.Environment]::SetEnvironmentVariable("DOCKER_HOST", "npipe:////./pipe/docker_cli", "User")
```

설정 후 **IntelliJ IDEA 재시작** 필요.

> `testcontainers.properties`나 Gradle `environment()` 방식은 Testcontainers가 `System.getenv("DOCKER_HOST")`를 직접 읽기 때문에 효과 없음.

## Testcontainers 버전

Testcontainers 1.x는 `DOCKER_HOST`를 올바르게 설정해도 Docker 29와의 API 통신에서 400 에러가 발생한다.
**2.0.2 이상**을 사용해야 한다.

```kotlin
// build.gradle.kts
testImplementation("org.testcontainers:testcontainers:2.0.2")
```

Spring Boot BOM이 관리하는 1.x 버전은 Docker 29 환경에서 동작하지 않으므로 반드시 버전을 명시할 것.