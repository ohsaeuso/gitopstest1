# Resilience4j 설정 정리

## 의존성

```kotlin
// app/build.gradle.kts
implementation("io.github.resilience4j:resilience4j-spring-boot3:2.3.0")
implementation("org.springframework.boot:spring-boot-starter-aop") // 어노테이션 방식에 필수
```

---

## 적용 위치 요약

| 패턴 | 인스턴스명 | 적용 위치 |
|---|---|---|
| Circuit Breaker | `departments` | `ExternalDepartmentClient.fetchDepartments`, `fetchDepartmentsAsync` |
| Retry | `departments` | `ExternalDepartmentClient.fetchDepartments` |
| Bulkhead | `departments` | `ExternalDepartmentClient.fetchDepartments` |
| TimeLimiter | `departments` | `ExternalDepartmentClient.fetchDepartmentsAsync` |
| Rate Limiter | `access` | `UserAccessService.recordAccess` |

---

## 인스턴스별 설정 (`application.yaml`)

### Circuit Breaker — `departments`

```yaml
resilience4j:
  circuitbreaker:
    instances:
      departments:
        sliding-window-type: COUNT_BASED   # 호출 횟수 기준
        sliding-window-size: 10            # 최근 10회 호출 평가
        failure-rate-threshold: 50         # 실패율 50% 초과 → OPEN
        slow-call-rate-threshold: 50       # 느린 호출 비율 50% 초과 → OPEN
        slow-call-duration-threshold: 2s   # 2초 초과 = 느린 호출
        wait-duration-in-open-state: 10s   # OPEN 상태 10초 유지 후 HALF_OPEN
        permitted-number-of-calls-in-half-open-state: 3  # HALF_OPEN에서 3회 탐색
        register-health-indicator: true    # Actuator /health 노출
```

**상태 전이:**
```
CLOSED → (실패율 50% 초과) → OPEN → (10초 후) → HALF_OPEN → (3회 성공) → CLOSED
                                                             → (실패) → OPEN
```

### Retry — `departments`

```yaml
resilience4j:
  retry:
    instances:
      departments:
        max-attempts: 3           # 최초 호출 포함 최대 3회
        wait-duration: 300ms      # 재시도 간 300ms 대기
        retry-exceptions:
          - java.lang.RuntimeException
        ignore-exceptions:
          - io.github.resilience4j.circuitbreaker.CallNotPermittedException
```

> Circuit Breaker가 OPEN일 때 던지는 `CallNotPermittedException`은 재시도하지 않음.

### Bulkhead — `departments`

```yaml
resilience4j:
  bulkhead:
    instances:
      departments:
        max-concurrent-calls: 5    # 최대 5개 동시 호출
        max-wait-duration: 100ms   # 대기 허용 시간 초과 시 BulkheadFullException
```

### TimeLimiter — `departments`

```yaml
resilience4j:
  timelimiter:
    instances:
      departments:
        timeout-duration: 1s            # 1초 초과 시 TimeoutException
        cancel-running-future: true     # 타임아웃 시 Future 취소
```

> `CompletableFuture` 반환 타입에만 적용됨. TimeLimiter에 의한 TimeoutException은 Circuit Breaker에서 slow call로 집계됨.

### Rate Limiter — `access`

```yaml
resilience4j:
  ratelimiter:
    instances:
      access:
        limit-for-period: 5      # 갱신 주기당 최대 5회
        limit-refresh-period: 10s # 10초마다 허용량 갱신
        timeout-duration: 0s     # 대기 없이 즉시 거부
```

---

## 어노테이션 적용 코드

### `ExternalDepartmentClient`

```kotlin
// 동기: CircuitBreaker → Retry → Bulkhead 순서로 적용
@CircuitBreaker(name = "departments", fallbackMethod = "departmentsFallback")
@Retry(name = "departments")
@Bulkhead(name = "departments")
fun fetchDepartments(username: String): List<String>

// 비동기: CircuitBreaker → TimeLimiter 순서로 적용 (CompletableFuture 필수)
@CircuitBreaker(name = "departments", fallbackMethod = "departmentsAsyncFallback")
@TimeLimiter(name = "departments")
fun fetchDepartmentsAsync(username: String): CompletableFuture<List<String>>
```

**어노테이션 실행 우선순위 (높을수록 먼저 감쌈):**
`Bulkhead > CircuitBreaker > RateLimiter > Retry > TimeLimiter > ... > 실제 메서드`

### `UserAccessService`

```kotlin
@RateLimiter(name = "access")
fun recordAccess(username: String)
```

---

## 예외 → HTTP 응답 매핑 (`GlobalExceptionHandler`)

| 예외 | HTTP 상태 | 발생 조건 |
|---|---|---|
| `CallNotPermittedException` | `503 Service Unavailable` | Circuit Breaker OPEN |
| `RequestNotPermitted` | `429 Too Many Requests` | Rate Limiter 초과 |
| `BulkheadFullException` | `503 Service Unavailable` | Bulkhead 동시 호출 초과 |

응답 형식은 RFC 7807 ProblemDetail.

---

## 테스트

### 실행 방법

```bash
# Resilience4j 테스트만 실행 (Oracle/Docker 불필요 — H2 인메모리 사용)
./gradlew :app:test --tests "*.ExternalDepartmentClientResilienceTest"
./gradlew :app:test --tests "*.UserAccessServiceResilienceTest"

# 전체 단위 테스트
./gradlew :app:test
```

> `@ActiveProfiles("local")`로 실행되므로 Docker 없이 H2로 컨텍스트가 뜸.

---

### `ExternalDepartmentClientResilienceTest`

파일: `app/src/test/kotlin/org/example/app/client/ExternalDepartmentClientResilienceTest.kt`

각 테스트 전 `@BeforeEach`에서 Circuit Breaker를 `CLOSED`로 초기화.

**Circuit Breaker**

- **`fetchDepartments_givenCircuitBreakerOpen_thenFallbackReturned`**
  - 검증: CB OPEN 시 `departmentsFallback` 호출 → `["unknown"]` 반환
  - 기법: `transitionToOpenState()` 강제 전환

- **`fetchDepartments_givenCircuitBreakerOpen_thenCallNotPermittedNotRetried`**
  - 검증: `CallNotPermittedException`은 `ignore-exceptions` 설정으로 재시도 안 함
  - 기법: retry 이벤트 리스너로 count = 0 확인

- **`fetchDepartments_givenCircuitBreakerHalfOpen_thenFailedProbesTransitionToOpen`**
  - 검증: HALF_OPEN 상태에서 3회 프로브 모두 실패 → OPEN 전이 (`permittedNumberOfCallsInHalfOpenState: 3` 동작 증명)
  - 기법: Bulkhead 소진으로 프로브 실패 강제 → `repeat(3)` 후 `cb.state == OPEN` 검증

**Retry**

- **`fetchDepartments_givenRuntimeException_thenRetriedUpToMaxAttempts`**
  - 검증: `RuntimeException` 발생 시 재시도 이벤트 정확히 2회 (`maxAttempts=3` = 초기 1 + 재시도 2)
  - 기법: `BulkheadFullException`(extends RuntimeException)을 Bulkhead 소진으로 결정론적 발생, retry 이벤트 리스너로 count 검증

**Bulkhead**

- **`fetchDepartments_givenBulkheadFull_thenFallbackReturned`**
  - 검증: 동시 슬롯 초과 시 fallback `["unknown"]` 반환
  - 기법: `bulkhead.acquirePermission()` × 5 로 슬롯 소진 → 다음 호출 거부 확인

**TimeLimiter**

- **`fetchDepartmentsAsync_givenCallExceedsTimeLimitOf1s_thenFallbackReturned`**
  - 검증: 2초 sleep이 1초 제한 초과 → `departmentsAsyncFallback` 호출 → `["unknown"]`
  - 기법: 실제 호출 (항상 타임아웃 발생)

- **`fetchDepartmentsAsync_givenTimeLimiterApplied_thenCompletesBeforeInternalSleepDeadline`**
  - 검증: 완료까지 경과 시간 < 2000ms (TimeLimiter가 1초에 실제로 차단했다는 증거)
  - 기법: 호출 전후 `System.currentTimeMillis()` 차이 측정

---

### `UserAccessServiceResilienceTest`

파일: `app/src/test/kotlin/org/example/app/service/UserAccessServiceResilienceTest.kt`

각 테스트 전 `@BeforeEach`에서 `rateLimiter.acquirePermission()`으로 잔여 허용량을 모두 소진.

**Rate Limiter**

- **`recordAccess_givenPermitsExhausted_thenRequestNotPermitted`**
  - 검증: 허용량 소진 후 `@RateLimiter` 호출 시 `RequestNotPermitted` throw
  - 기법: `@BeforeEach`에서 `acquirePermission()`으로 직접 허용량 소진

- **`access_rateLimiterConfig_matchesYaml`**
  - 검증: limitForPeriod=5, limitRefreshPeriod=10s, timeoutDuration=0s
  - 기법: `RateLimiterRegistry`에서 config 직접 읽기 (yaml 바인딩 확인용)

---

### 테스트 설계 원칙

**결정론적 실행을 위한 핵심 기법: Bulkhead permit 수동 소진**

`simulateUnstableExternalCall()`이 40% 확률로 실패하기 때문에 `RuntimeException`을 결정론적으로 발생시킬 수 없다. 대신 `BulkheadFullException`(extends `RuntimeException`)을 활용한다.

```
bulkhead.acquirePermission() × 5  →  다음 메서드 호출 시 BulkheadFullException 발생
                                   →  retry-exceptions: [RuntimeException] 에 해당 → 재시도
                                   →  최종 실패 시 CircuitBreaker fallback 호출
```

이 기법 하나로 Retry·Bulkhead·HALF_OPEN 전이 테스트를 모두 랜덤성 없이 구성할 수 있다.

**상태 격리**

- CB 상태: `@BeforeEach`에서 `transitionToClosedState()` 초기화
- Bulkhead permit: 소진한 테스트에서 `finally` 블록 안에 `bulkhead.onComplete()` × 5 로 반납
- Rate Limiter permit: `@BeforeEach`에서 `acquirePermission()` 반복으로 소진
- retry 이벤트 리스너: 각 테스트가 로컬 `AtomicInteger`를 캡처 — 누적 등록돼도 다른 테스트의 카운터에 영향 없음

---

## Actuator 확인

```bash
# Circuit Breaker 상태
GET /actuator/health

# 전체 메트릭
GET /actuator/metrics/resilience4j.circuitbreaker.state?tag=name:departments
GET /actuator/metrics/resilience4j.ratelimiter.available.permissions?tag=name:access
```