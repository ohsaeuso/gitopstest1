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

## FAQ — OAuth2 Client 인증 관련 추가 논의 (2026-07-31)

### private_key_jwt는 그냥 우리가 아는 JWT인가?

포맷(header.payload.signature)은 동일. 다른 건 **용도** — access/id token이 아니라 **client assertion**(RFC 7523)이다.

- `iss` = `sub` = client_id (본인 증명)
- `aud` = 토큰 엔드포인트 URL
- `exp`, `jti`(재사용 방지 nonce)
- client 자신의 private key로 서명 → 서버는 등록된 public key(JWKS)로 검증
- 요청 시 `client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer` + `client_assertion=<JWT>`

### OAuth2 client 인증 방식 전체 지도

| 방식 | 설명 |
| --- | --- |
| `client_secret_basic` | HTTP Basic 헤더에 client_id:secret |
| `client_secret_post` | body에 client_id/secret |
| `private_key_jwt` (RFC 7523) | 비대칭키로 서명한 JWT assertion |
| `client_secret_jwt` (RFC 7523) | 위와 같은 RFC지만 **공유 secret으로 HMAC** 서명 |
| `tls_client_auth` / `self_signed_tls_client_auth` (mTLS, RFC 8705) | 클라이언트 인증서로 TLS 레벨 인증 |
| `none` | public client, secret 없음 (PKCE로 대체) |

### client_secret은 정말 안 쓰나? — 아니다, 여전히 압도적 다수

- Keycloak/Auth0/Okta/Azure AD/Google 전부 confidential client 기본값이 `client_secret_basic`.
- secret을 배제하는 건 특정 맥락뿐: **public client**(SPA/모바일, 애초에 secret 못 숨김 → PKCE), **FAPI**(오픈뱅킹, 명시적으로 secret 금지), **워크로드 규모가 큰 환경**(K8s 등, 이 문서의 동기).

### OAuth 2.1이 바꾼 것 / 안 바꾼 것

- **제거:** Implicit grant, Resource Owner Password Credentials grant
- **강제:** authorization_code grant 쓰는 모든 client(confidential 포함)에 PKCE
- **비권장(제거 아님):** `client_secret_post` (body에 secret) → Basic 헤더 권장
- **안 바뀜:** `client_secret_basic`은 confidential client 인증 방식으로 여전히 유효. "OAuth 2.1은 client secret 지원 안 함"은 오해 — public client는 원래도 못 썼던 것뿐.

### grant_type과 client 인증 방식은 별개 축

`private_key_jwt`는 grant_type이 아니다. "토큰을 어떤 흐름으로 받나"(`authorization_code`, `client_credentials`, `refresh_token`, `token-exchange`)와 "`/token` 호출 시 나를 어떻게 증명하나"(`client_secret_basic`, `private_key_jwt`, mTLS)는 조합 가능한 별개 파라미터. `authorization_code` + `private_key_jwt` 조합도 정상.

### private_key_jwt 서명 방법

```
header:  { "alg": "RS256", "kid": "key-1" }   // RS256/PS256/ES256 (비대칭) — HS256은 client_secret_jwt용
payload: {
  "iss": "ecommerce-api", "sub": "ecommerce-api",
  "aud": "<token endpoint>", "jti": "<nonce>",
  "exp": now+60s, "iat": now
}
```

`kid`로 키 로테이션 시 무중단 전환(JWKS에 신·구 키 동시 노출) 가능. `exp`는 짧게(1~5분) 잡아 탈취 시 재사용 창 최소화.

### private_key_jwt도 결국 키 관리 부담은 못 없앤다

- **해결하는 것:** secret이 네트워크를 안 탐 (전송 구간 유출 위험 제거).
- **해결 못 하는 것:** private key를 client 쪽에 **장기 보관·로테이션**해야 하는 문제는 client_secret과 본질적으로 동급 (대칭키 vs 비대칭키 차이일 뿐). → K8s에서 Federated 방식(방법 B)을 쓰는 이유: 키 관리 책임을 애플리케이션 팀 → 플랫폼(K8s)으로 이전.

### 방법 A가 비권장인 이유 (iss/sub 둘 다 안 맞음)

Keycloak 내장 Signed Jwt 검증기는 RFC 7523의 **자기 서명 모델**(`iss == sub == client_id`, 자기 JWKS에서만 키 조회)을 전제로 한다. K8s SA 토큰은:

```json
{ "iss": "https://oidc.eks.<region>.amazonaws.com/id/<cluster-id>",
  "sub": "system:serviceaccount:orders-ns:orders-sa" }
```

`iss`(클러스터 URL)도 `sub`(K8s 포맷)도 client_id와 무관 → 커스텀 SPI로 `sub`/`iss` 체크와 JWKS resolution까지 다 우회해야 함. 결국 방법 B(Token Exchange, 처음부터 "외부 issuer" 개념을 표준 지원)가 이미 해주는 걸 재발명하는 셈 + Keycloak 버전 업그레이드 시 SPI 깨질 리스크. **어거지 — 이론상 가능하나 실무에서 할 이유 없음.**

### mTLS를 안 쓰는 이유

1. K8s가 짧은 수명 client 인증서를 기본 제공 안 함 — 서비스 메시(Istio/Linkerd)나 SPIFFE/SPIRE 같은 추가 인프라 필요.
2. PKI 관리 부담(CA, 폐기/CRL) — private key 관리 문제가 인증서+CA 관리로 확장.
3. Ingress/LB가 보통 TLS를 종료하기 때문에 client cert 정보가 Keycloak까지 안 전달됨 — 전체 네트워크 경로를 mTLS-aware하게 재구성해야 함.

### JWKS는 어디서 관리하나

| JWKS | 관리 주체 |
| --- | --- |
| K8s SA 토큰용 (`/openid/v1/jwks`) | kube-apiserver 자동 호스팅. EKS/GKE는 클라우드 관리, 온프렘은 외부 노출만 필요 |
| Keycloak 자신의 realm 키 (`/realms/{realm}/protocol/openid-connect/certs`) | Keycloak이 자체 관리 (Realm Settings → Keys) |
| (참고) private_key_jwt를 실제 썼다면 | 우리(client)가 직접 호스팅하거나 Keycloak client 설정에 업로드 — 방법 B를 쓰는 한 해당 없음 |

### K8s SA 토큰 로테이션 주기

- **토큰 TTL**(`expirationSeconds`, 예: 600초)과 **서명 키 로테이션**은 별개.
- vanilla K8s는 서명 키 자동 로테이션 없음 — 관리자가 수동으로 신·구 키를 JWKS에 동시 노출하며 전환(무중단), 주기는 조직 정책에 달림.
- 매니지드 클라우드(EKS/GKE)는 내부적으로 관리하나 공개된 고정 주기는 없음 — 필요 시 각 클라우드 문서 확인.

### Keycloak jwksUrl 캐싱 주기

- 고정 주기 폴링이 아니라 **캐시 + `kid` 미스 시 lazy refetch** 모델. 반복 미스로 인한 DoS 방지용 최소 fetch 간격(rate limit)이 있으나 정확한 기본값은 버전마다 달라 별도 확인 필요 (TODO 참고).
- 확인 방법: Admin Console Keys 설정 확인, 또는 실제 키 전환 후 `org.keycloak.keys` DEBUG 로그로 refetch 시점 관찰.

### "K8s 컨트롤러 = KMS"인가?

정확히는 KMS보다 **워크로드 아이덴티티 발급자(OIDC IdP / STS)**에 가깝다.

- **KMS와 같은 점:** 키 자체는 절대 밖으로 안 나가고, "서명 연산"만 서비스로 제공.
- **다른 점:** 범용 서명이 아니라 **고정 포맷의 SA 신원 클레임 하나만** 발급. AWS STS `AssumeRoleWithWebIdentity`, GCP Workload Identity Federation과 같은 포지션.
- "발급"은 "서명"보다 무거운 개념 — 서명 + **정책 판단**(이 요청자가 정말 이 SA인지, 이 aud로 요청할 자격이 있는지, TTL을 얼마나 줄지, 클레임에 뭘 넣을지)이 포함된 것. 이 판단이 이미 일종의 1차 인가라서, Keycloak이 그 클레임을 믿고 role 매핑을 할 수 있는 신뢰 기반이 됨.

### K8s + Keycloak 환경이어도 방법 B(Token Exchange)를 안 쓰는 이유

기술적으론 딱 맞는 조합이지만, 실무에서 도입을 미루게 되는 이유들:

1. **버전 요건이 허들** — Token Exchange V2는 **Keycloak 26.2+ GA**. 그 이전은 legacy V1(preview)이라 프로덕션에 걸기 불안정. 이 기능 하나 때문에 메이저 버전을 올리는 건 회귀 테스트까지 딸려오는 큰 프로젝트.
2. **FGAP(Fine-Grained Admin Permission) 자체가 아직 무겁다** — Token Exchange를 쓰려면 FGAP를 켜고 세밀한 정책을 짜야 하는데, FGAP도 상대적으로 성숙도가 낮은 기능. 덜 검증된 기능 두 개를 프로덕션 인증 경로에 동시에 얹는 리스크.
3. **복잡도가 없어지는 게 아니라 이동함** — 이미 Vault 등으로 secret 자동 로테이션이 돌아가는 조직이면, Token Exchange로 갈아타는 순간 IDP 등록·매퍼·FGAP 정책이라는 **새 관리 대상**이 생기는 것뿐. 클러스터가 여러 개면 등록해야 할 IDP도 그만큼 늘어남.
4. **온프렘이면 K8s API 서버 메타데이터를 외부 노출**해야 함 — JWKS 자체는 공개해도 안전하지만, "컨트롤 플레인 인접 엔드포인트는 무조건 외부 노출 금지"라는 보안 정책에 걸리는 조직이 많음.
5. **벤더 락인** — IDP 브로커링 + FGAP + Token Exchange V2 조합은 Keycloak 특화 구성. 나중에 다른 IdP로 이전할 계획이 있다면 이 투자가 안 옮겨감. 반면 `client_secret_basic`/`private_key_jwt`는 어디서든 표준 지원.
6. **감사·팀 숙련도 비용** — 컴플라이언스 담당자는 "secret 로테이션 주기"는 익숙해도 "SA 토큰을 외부 IdP로 등록해 교환"하는 흐름은 생소해 매번 설명 필요. 이해하는 사람이 소수면 버스 팩터 리스크, 장애 시 디버깅 표면도 넓음(토큰 발급 → 네트워크 경로 → IDP 등록/JWKS fetch → FGAP 정책 → 매퍼, 5~6단계).

**단, 위 3번은 전제에 따라 뒤집힘:** 만약 조직에 **Vault/Secrets Manager가 아직 없다면**, `client_secret` + 자동 로테이션을 하려 해도 결국 Vault를 새로 구축해야 함(HA, auto-unseal, Agent Injector, 백업/DR까지 — 이것도 "지켜야 할 새 시스템"). 이 경우 Token Exchange는 **이미 떠 있는 Keycloak에 설정만 얹는** 것이라 오히려 신규 인프라 도입 비용이 더 낮을 수 있음. (단, Vault를 이 문제 하나가 아니라 다른 secret까지 포함해 어차피 도입할 계획이라면 얘기가 다시 달라짐.)

**결론:** 지금 이 프로젝트 규모/현황(Keycloak 버전 미확정, 클러스터 종류 미확정)에서는 과설계 리스크가 크므로, 방법 B는 "옵션 지도"로 남겨두고 실제 구현은 가벼운 방식(`client_secret` + 로테이션, 또는 Vault 부재 시 상황에 따라 Token Exchange)으로 시작. 버전/클러스터 확정 후 재검토.

## TODO / 다음 단계

- [ ] 실제 Keycloak 버전 확인 (26.2+ 여부, "25.x대"로 기억하나 정확한 값 재확인 필요 — `kc.sh --version` 또는 배포 이미지 태그)
- [ ] 클러스터 종류 확정 (EKS / GKE / 온프렘) — issuer 노출 방식이 달라짐
- [ ] 사내 Vault/Secrets Manager 도입 여부 확인 — 없다면 방법 B 쪽으로 무게추가 기울 수 있음
- [ ] ECOM 이슈 생성 후 `/jira-pull`로 컨텍스트 당겨 구현 시작