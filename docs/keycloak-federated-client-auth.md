# Keycloak Federated Client Authentication (K8s)

> 클라이언트가 정적 secret/private key 없이, K8s ServiceAccount 토큰으로 Keycloak client 인증을 수행하는 방법 정리.

## 배경

Confidential client(백엔드 서비스)가 Keycloak에 자신을 인증하는 전통적 방식:

- Client ID + Secret (`client_secret_basic` / `client_secret_post`)
- Signed JWT (`private_key_jwt`, RFC 7523)
- X.509 인증서 (mTLS)

공통 문제: **장기 비밀을 클라이언트 측에 저장·로테이션**해야 함. K8s Pod, CI/CD 파이프라인 환경에서는 이게 운영 부담이자 유출 리스크.

## Federated Client Authentication

클라이언트가 자체 비밀 없이, **신뢰하는 외부 IdP(OIDC issuer)가 발급한 JWT**를 client assertion으로 제출하는 방식. AWS/GCP Workload Identity Federation, GitHub Actions OIDC-to-cloud 인증과 동일한 패턴.

## K8s 환경에서의 두 가지 구현 방법

### 방법 A: ServiceAccount 토큰을 Signed JWT client assertion으로 사용

K8s 1.20+는 SA 토큰을 OIDC 호환 JWT로 발급, API 서버가 JWKS 엔드포인트(`/openid/v1/jwks`) 공개. Keycloak의 "Signed Jwt" client authenticator가 이 JWKS URL을 신뢰 소스로 쓰도록 설정.

**한계:** Keycloak 기본 Signed Jwt 검증은 `iss`/`sub`가 client_id와 동일할 것을 기대. K8s SA 토큰의 `sub`(`system:serviceaccount:<ns>:<sa-name>`)는 그대로 안 맞아 **커스텀 Keycloak SPI(ClientAuthenticator)** 구현이 필요한 경우가 많음.

### 방법 B: Token Exchange (V2) — 권장

"외부 issuer가 서명한 JWT"를 "Keycloak이 발급한 access token"으로 교환. Vault K8s auth method와 유사한 패턴. **Keycloak 26.2+ (Standard Token Exchange V2, GA) 필요** — 그 이전은 legacy V1(preview)이라 동작 방식이 다름.

```
Pod → (projected SA token) → Keycloak /token 엔드포인트
     grant_type=urn:ietf:params:oauth:grant-type:token-exchange
     subject_token=<K8s SA JWT>
     subject_token_type=urn:ietf:params:oauth:token-type:jwt
     subject_issuer=<등록한 IDP alias>
                     ↓
Keycloak: K8s issuer JWKS로 서명 검증 → 매핑 규칙 적용 → 내부 access token 발급
```

#### 설정 단계

**1. K8s 쪽 — audience 지정된 projected SA token**

```yaml
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: app
      volumeMounts:
        - name: keycloak-token
          mountPath: /var/run/secrets/keycloak
  volumes:
    - name: keycloak-token
      projected:
        sources:
          - serviceAccountToken:
              path: token
              audience: "keycloak"
              expirationSeconds: 600
```

클러스터 issuer/JWKS 확인:

```bash
kubectl get --raw /.well-known/openid-configuration
kubectl get --raw /openid/v1/jwks
```

(EKS/GKE는 클러스터 생성 시 공인 issuer URL이 이미 공개됨. 온프렘은 API 서버 앞단에 이 엔드포인트를 외부 노출해야 함.)

**2. Keycloak — K8s 클러스터를 Identity Provider(OIDC)로 등록**

```bash
kcadm.sh create identity-provider/instances -r ecommerce \
  -s alias=k8s-cluster-prod \
  -s providerId=oidc \
  -s enabled=true \
  -s config.issuer=https://<k8s-oidc-issuer> \
  -s config.jwksUrl=https://<k8s-api-server>/openid/v1/jwks \
  -s config.useJwksUrl=true \
  -s config.clientAuthentication=none
```

**3. 해당 IDP에 Token Exchange 권한 부여**

Fine-grained admin permission 활성화 후, 이 IDP를 통한 토큰 교환을 허용할 클라이언트를 명시 (기본은 전면 차단):

```
Identity Providers → k8s-cluster-prod → Permissions → Enabled: ON
→ "token-exchange" permission → Policy에 "ecommerce-api" 클라이언트 허용 추가
```

**4. IDP Mapper로 SA 클레임 → role/attribute 매핑**

SA 토큰 클레임 예시:

```json
{
  "iss": "https://<k8s-issuer>",
  "sub": "system:serviceaccount:orders-ns:orders-sa",
  "kubernetes.io": {
    "namespace": "orders-ns",
    "serviceaccount": { "name": "orders-sa" }
  },
  "aud": ["keycloak"]
}
```

`orders-ns` 네임스페이스 SA만 `ROLE_ORDERS_SERVICE`를 받도록 조건부 Attribute/Hardcoded Role mapper 구성.

**5. 클라이언트 호출**

```
POST /realms/ecommerce/protocol/openid-connect/token
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
client_id=ecommerce-api
subject_token=<K8s SA JWT>
subject_token_type=urn:ietf:params:oauth:token-type:jwt
subject_issuer=k8s-cluster-prod
```

## 비교

| | 방법 A (Signed JWT + JWKS) | 방법 B (Token Exchange) |
|---|---|---|
| 순정 Keycloak으로 가능? | 제한적 (커스텀 SPI 필요 가능성 높음) | 대부분 표준 기능으로 커버 |
| 매핑 유연성 | 낮음 | namespace/SA별 세밀한 role 매핑 가능 |
| 운영 복잡도 | SPI 유지보수 부담 | Realm 설정 + 매핑 규칙 관리 |

**결론: 방법 B (Token Exchange) 권장.**

## 이 저장소(헥사고날 구조)에 적용 시 구현 방향

```
application/port/out/TokenExchangePort.kt          # 순수 인터페이스, domain 타입만 노출
adapter/out/client/KeycloakTokenExchangeClient.kt   # WebClient + resilience4j (CircuitBreaker/Retry)
```

- `TokenExchangePort`는 `fun exchange(subjectToken: String): AccessToken` 수준의 최소 계약만 노출 (Keycloak 응답 DTO를 도메인 밖으로 유출 금지)
- SA 토큰 경로(`/var/run/secrets/keycloak/token`) 읽기는 어댑터 내부에 캡슐화
- 실패 시 `DomainException` 하위 타입(예: `TokenExchangeFailedException`)으로 변환 → `GlobalExceptionHandler`가 RFC 7807로 변환

## 테스트

`test-patterns` 스킬 기준: Testcontainers로 **Keycloak 컨테이너**를 띄우고, K8s issuer 역할은 목 OIDC issuer(자체 서명 JWKS 서버 또는 WireMock)로 대체해 `subject_issuer` 플로우를 통합 테스트.

## TODO / 다음 단계

- [ ] 실제 Keycloak 버전 확인 (26.2+ 여부)
- [ ] 클러스터 종류 확정 (EKS / GKE / 온프렘) — issuer 노출 방식이 달라짐
- [ ] ECOM 이슈 생성 후 `/jira-pull`로 컨텍스트 당겨 구현 시작