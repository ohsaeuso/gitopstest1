# Local Dev Setup

## Windows + Docker Desktop (WSL2)

Testcontainers는 기본적으로 `\\.\pipe\docker_engine`에 연결을 시도하지만, Docker Desktop 최신 버전은 이 파이프를 실제 엔진으로 연결하지 않고 400 응답을 반환한다.

실제 Docker Desktop WSL2 엔진 파이프는 `\\.\pipe\dockerDesktopLinuxEngine`이므로, 아래 환경변수를 Windows 사용자 환경변수로 설정해야 한다.

```powershell
[System.Environment]::SetEnvironmentVariable("DOCKER_HOST", "npipe:////./pipe/dockerDesktopLinuxEngine", "User")
```

설정 후 **IntelliJ IDEA 재시작** 필요.

> `testcontainers.properties`나 Gradle `environment()` 방식은 Testcontainers가 `System.getenv("DOCKER_HOST")`를 직접 읽기 때문에 효과 없음.