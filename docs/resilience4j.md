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
        #wait-duration-in-open-state: 10s   # OPEN 상태 10초 유지 후 HALF_OPEN, 캐시가 붙어야 "10초 동안 캐시로 버틴다"는 의미가 생기고, 그때 비로소 이 숫자를 튜닝할 이유가 생깁니다
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
요약하면, 이 테스트 클래스는 "설정값(yaml)이 코드에 올바르게 바인딩되고, 어노테이션 순서/fallback 위치 설계가 의도대로 작동하는가"를 실제 컴포넌트(mock 없이 진짜 resilience4j registry) 기준으로     
검증하며, 외부 콜의 40% 랜덤 실패를 Bulkhead 소진이라는 결정론적 트리거로 우회하는 것이 설계의 핵심 트릭입니다.

```kotlin
// 동기: CircuitBreaker → Retry → Bulkhead 순서로 적용
@CircuitBreaker(name = "departments", fallbackMethod = "departmentsFallback")
@Retry(name = "departments")
@Bulkhead(name = "departments")
fun fetchDepartments(username: String): DepartmentLookupResult

// 비동기: CircuitBreaker → TimeLimiter 순서로 적용 (CompletableFuture 필수)
@CircuitBreaker(name = "departments", fallbackMethod = "departmentsAsyncFallback")
@TimeLimiter(name = "departments")
fun fetchDepartmentsAsync(username: String): CompletableFuture<DepartmentLookupResult>
```

`ExternalDepartmentClient`는 DB를 모르며, `fromFallback` 플래그만 반환한다. pending 요청 큐잉(outbox 저장)은 `DepartmentLookupService`(서비스 레이어)로 분리되어 있다 — `fallbackMethod`는 원본 메서드와 파라미터 시그니처가 같아야 하므로, 그 안에서 직접 DB에 쓰면 필요한 컨텍스트가 파라미터로 제한되는 한계가 있었음.

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
  - CB를 강제로 OPEN 전환 후 호출하면 실제 호출 없이 즉시 fallback(["unknown"], fromFallback=true)이 반환되는지 확인
  - 검증: CB OPEN 시 `departmentsFallback` 호출 → `["unknown"]` 반환
  - 기법: `transitionToOpenState()` 강제 전환

- **`fetchDepartments_givenCircuitBreakerOpen_thenCallNotPermittedNotRetried`**
  - OPEN 상태에서 나오는 CallNotPermittedException이 ignore-exceptions 설정 덕분에 재시도되지 않음(retry 이벤트 카운트 0)을 확인. Retry 설정의   
    ignore-exceptions가 실제로 작동하는지가 핵심.
  - 검증: `CallNotPermittedException`은 `ignore-exceptions` 설정으로 재시도 안 함
  - 기법: retry 이벤트 리스너로 count = 0 확인. Retry 설정의 ignore-exceptions가 실제로 작동하는지가 핵심.

- **`fetchDepartments_givenCircuitBreakerHalfOpen_thenFailedProbesTransitionToOpen`**
  - resilience4j는 CLOSED에서 HALF_OPEN으로 직접 못 가므로(주석에 명시) OPEN → HALF_OPEN을 거친 뒤, Bulkhead를 소진시켜 3번의 프로브 호출을
     모두 실패시키고 permitted-number-of-calls-in-half-open-state: 3이 소진되는 즉시 다시 OPEN으로 돌아가는지 검증.
  - 검증: HALF_OPEN 상태에서 3회 프로브 모두 실패 → OPEN 전이 (`permittedNumberOfCallsInHalfOpenState: 3` 동작 증명)
  - 기법: Bulkhead 소진으로 프로브 실패 강제 → `repeat(3)` 후 `cb.state == OPEN` 검증

- **`fetchDepartments_givenCircuitBreakerHalfOpen_thenSuccessfulProbesTransitionToClosed`**
  - 위 실패 시나리오의 반대쪽: HALF_OPEN 프로브 3회가 모두 성공하면 CLOSED로 복귀하는지 검증.
  - 검증: HALF_OPEN 상태에서 3회 프로브 모두 성공 → CLOSED 전이
  - 기법: 실제 호출(40% 랜덤 실패) 대신 `cb.onSuccess(0, TimeUnit.MILLISECONDS)`를 3회 직접 호출해 성공 결과를 CB API로 주입 → 결정론적 재현

- **`fetchDepartments_givenFailureRateExceedsThreshold_thenCircuitBreakerOpensFromClosed`**
  - 지금까지의 CB 테스트는 전부 `transitionToOpenState()`로 상태를 수동 강제 전환한 뒤 시작했는데, 이 테스트는 `failure-rate-threshold: 50`,
    `sliding-window-size: 10`이 실제로 CLOSED 상태에서 스스로 OPEN을 트리거하는지(수동 전환 없이) 검증.
  - 검증: CLOSED 상태에서 10회 중 10회 실패(100% > 50%) → 자동으로 OPEN 전이
  - 기법: `cb.onError(0, TimeUnit.MILLISECONDS, RuntimeException(...))`를 10회(sliding-window-size만큼) 직접 호출해 실패를 CB API로 주입

- **`fetchDepartments_givenSlowCallRateExceedsThreshold_thenCircuitBreakerOpensFromClosed`**
  - `slow-call-duration-threshold: 2s`, `slow-call-rate-threshold: 50`이 실패 없이 "느린 성공"만으로도 OPEN을 트리거하는지 검증 (기존 테스트들은
    전부 실패/타임아웃 경로만 다뤘고 slow-call 자체를 검증하는 테스트가 없었음).
  - 검증: CLOSED 상태에서 2s 임계값을 초과하는 소요시간(3s)의 성공 호출 10회 → slow-call-rate 100% > 50% → 자동으로 OPEN 전이
  - 기법: `cb.onSuccess(3, TimeUnit.SECONDS)`를 10회 직접 호출. `CircuitBreakerMetrics.onSuccess()`가 `durationUnit.toNanos(duration) > slowCallDurationThresholdInNanos`이면 `SLOW_SUCCESS`로 기록하는 것을 이용(실제 지연 없이 시간 값만 주입해 빠르고 결정론적).

- **`departments_circuitBreakerConfig_matchesYaml`**
  - 검증: slidingWindowType=COUNT_BASED, slidingWindowSize=10, failureRateThreshold=50, slowCallRateThreshold=50, slowCallDurationThreshold=2s, permittedNumberOfCallsInHalfOpenState=3
  - 기법: `CircuitBreakerRegistry`에서 config 직접 읽기 (yaml 바인딩 확인용, `access_rateLimiterConfig_matchesYaml`과 동일한 패턴)

**Retry(max-attempts: 3, wait-duration: 300ms)**

```txt
ExternalDepartmentClient.kt:18-23의 주석이 설명하듯:                                                                                                                                                  
  @CircuitBreaker(name = "departments")                                                                                                                                                                 
  @Retry(name = "departments", fallbackMethod = "departmentsFallback")                                                                                                                                  
  @Bulkhead(name = "departments")                                                                                                                                                                       
  Spring AOP의 고정 적용 순서상 @Retry가 @CircuitBreaker보다 바깥쪽입니다. 만약 fallback을 @CircuitBreaker에 붙이면 첫 시도에서 발생한 모든 예외(Bulkhead 거부 포함)를 CB가 즉시 삼켜버려서 Retry가 아예
  실행되지 않습니다. 그래서 fallback은 @Retry에 붙어 있고, 테스트 fetchDepartments_givenRuntimeException_thenRetriedUpToMaxAttempts가 바로 이 배치가 "재시도가 실제로 일어남"을 보장하는지 확인하는     
  테스트입니다.
```

- **`fetchDepartments_givenRuntimeException_thenRetriedUpToMaxAttempts`**
  - Bulkhead 소진으로 확정적 RuntimeException을 만든 뒤, retry 이벤트가 정확히 2회 발생하는지 확인(최초 1회 + 재시도 2회 = max-attempts 3).
  - 검증: `RuntimeException` 발생 시 재시도 이벤트 정확히 2회 (`maxAttempts=3` = 초기 1 + 재시도 2)
  - 기법: `BulkheadFullException`(extends RuntimeException)을 Bulkhead 소진으로 결정론적 발생, retry 이벤트 리스너로 count 검증

**Bulkhead(max-concurrent-calls: 5)**

- **`fetchDepartments_givenBulkheadFull_thenFallbackReturned`**
  - 검증: 동시 슬롯 초과 시 fallback `["unknown"]` 반환
  - 기법: `bulkhead.acquirePermission()` × 5 로 슬롯 소진 → 다음 호출 거부 확인

**TimeLimiter(timeout-duration: 1s, cancel-running-future: true, 비동기 경로만 적용)**

- **`fetchDepartmentsAsync_givenCallExceedsTimeLimitOf1s_thenFallbackReturned`**
  - 슬롯 5개를 다 채운 뒤 호출하면 최종적으로 fallback이 반환되는지(Bulkhead 거부 → Retry 소진 → CB fallback으로 이어지는 체인 전체 확인).
  - 검증: 2초 sleep이 1초 제한 초과 → `departmentsAsyncFallback` 호출 → `["unknown"]` (내부에서 2초 sleep하는 비동기 호출이 1초 제한에 걸려 fallback으로 대체되는지 확인)
  - 기법: 실제 호출 (항상 타임아웃 발생)

- **`fetchDepartmentsAsync_givenTimeLimiterApplied_thenCompletesBeforeInternalSleepDeadline`**
  - 실제 완료 시간이 2000ms보다 짧다는 것으로 "1초 제한이 실제로 걸렸다"는 것을 시간 측정으로 방증(간접 증거지만 명시적인 타임아웃 예외
    캐치보다 TimeLimiter가 Future를 실제로 취소했음을 보여주는 방식).
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

```txt
실제 메서드는 이렇게 되어 있습니다 (ExternalDepartmentClient.kt:49-53):                                                                                                                               
private fun simulateUnstableExternalCall() {                                                                                                                                                          
if (Math.random() < 0.4) throw RuntimeException("External department service unavailable")                                                                                                        
}                                                                                                                                                                                                     
40% 확률로만 실패하기 때문에, "실패 시 재시도된다"를 검증하려고 그냥 여러 번 호출하면 테스트가 flaky해집니다. 그래서 이 테스트 스위트는 Bulkhead의 permit을 미리 다 소진시켜                          
BulkheadFullException(RuntimeException의 하위 타입)을 100% 확정적으로 발생시키는 트릭을 씁니다. application.yaml의 bulkhead.instances.departments.max-concurrent-calls: 5를 이용해                    
bulkhead.acquirePermission()을 5번 직접 호출해 슬롯을 채우면, 이후 client.fetchDepartments() 호출은 항상 BulkheadFullException으로 막히고, 이 예외는 retry-exceptions: [RuntimeException]에 해당하므로
Retry가 개입하게 됩니다. 이 하나의 기법으로 Retry, Bulkhead, HALF_OPEN 전이 테스트를 모두 랜덤성 없이 구성합니다.
```

`simulateUnstableExternalCall()`이 40% 확률로 실패하기 때문에 `RuntimeException`을 결정론적으로 발생시킬 수 없다. 대신 `BulkheadFullException`(extends `RuntimeException`)을 활용한다.

```
bulkhead.acquirePermission() × 5  →  다음 메서드 호출 시 BulkheadFullException 발생
                                   →  retry-exceptions: [RuntimeException] 에 해당 → 재시도
                                   →  최종 실패 시 CircuitBreaker fallback 호출
```

이 기법 하나로 Retry·Bulkhead·HALF_OPEN 전이 테스트를 모두 랜덤성 없이 구성할 수 있다.

**결정론적 실행을 위한 두 번째 기법: CircuitBreaker API로 결과 직접 주입**

CLOSED→OPEN 자연 전이(failure-rate), slow-call-rate 전이, HALF_OPEN→CLOSED(성공) 전이는 Bulkhead 트릭으로 재현하기 어렵거나(성공을 결정론적으로 만들 수 없음) 굳이 실제 지연을 기다릴 필요가 없는 경우다. 이때는 `client.fetchDepartments()`를 호출하는 대신 `CircuitBreaker` 인스턴스에 결과를 직접 주입한다:

```txt
cb.onSuccess(0, TimeUnit.MILLISECONDS)                              →  성공 1회 기록
cb.onError(0, TimeUnit.MILLISECONDS, RuntimeException(...))         →  실패 1회 기록
cb.onSuccess(3, TimeUnit.SECONDS)                                    →  slow-call-duration-threshold(2s) 초과 → SLOW_SUCCESS로 기록
```

`CircuitBreakerMetrics.onSuccess(duration, unit)`은 내부적으로 `durationUnit.toNanos(duration) > slowCallDurationThresholdInNanos`이면 `SLOW_SUCCESS`로, 아니면 `SUCCESS`로 기록한다(`onError`도 동일하게 `SLOW_ERROR`/`ERROR` 구분). 즉 실제로 3초를 기다리지 않고도 `duration` 인자에 3초를 넘겨주는 것만으로 "느린 호출"을 재현할 수 있다. 이 방식은 실제 메서드를 거치지 않으므로 CB 설정값(threshold, sliding-window-size)만 정확히 검증하고 싶을 때 Bulkhead 트릭보다 더 빠르고 단순하다.

**상태 격리 전략**

- CB 상태: `@BeforeEach`에서 `transitionToClosedState()` 초기화
- Bulkhead permit: 소진한 테스트에서 `finally` 블록 안에 `bulkhead.onComplete()` × 5 로 반납 (Bulkhead permit을 수동 소진한 테스트는 반드시 finally 블록에서 onComplete()로 반납해 다음 테스트에 영향 주지 않음.)
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