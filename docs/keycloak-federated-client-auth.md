# Keycloak Federated Client Authentication (K8s)

> 클라이언트가 정적 secret/private key 없이, K8s ServiceAccount 토큰으로 Keycloak client 인증을 수행하는 방법 정리.

> ## ⚠️ 업데이트 (2026-08-08) — 아래 "방법 B 권장" 결론은 재검토 대상
>
> [keycloak-token-exchange.md](./keycloak-token-exchange.md)에서 방법 B(Token Exchange)를 실제로 끝까지 구현하다가, **K8s SA 토큰에 `typ` 헤더가 없어서 Keycloak 24.0.3+의 강화된 검증(RFC 9068)에 구조적으로 막힌다는 것**을 확인함 — 설정으로 해결 불가, 두 시스템 조합 자체의 비호환.
>
> 동시에, Keycloak이 그 사이 **"Federated Client Authentication"이라는 이름의 신규 네이티브 기능**을 26.4(preview)~26.6(GA)에 걸쳐 출시했다는 걸 발견함 — 이름이 이 문서 제목과 겹치지만 **별개의 구체적인 Keycloak 제품 기능**. 아래 "방법 A"가 2026-07-31 당시 "커스텀 SPI 필요해서 비권장"이라 결론 낸 바로 그 문제(iss/sub가 client_id와 안 맞음)를 **순정 기능으로 네이티브 해결**한 것으로 보임. 자세한 내용은 아래 "Federated Client Authentication — 신규 네이티브 기능" 섹션 참고.
>
> **현재 결론: 방법 B는 이 유스케이스에서 포기. 방법 A를 신규 네이티브 기능으로 재시도할 예정** (홈랩 Keycloak 26.2.4 → 26.7.1 업그레이드 필요, 아직 미실행).

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

**한계 (2026-07-31 당시 기준):** Keycloak 기본 Signed Jwt 검증은 `iss`/`sub`가 client_id와 동일할 것을 기대. K8s SA 토큰의 `sub`(`system:serviceaccount:<ns>:<sa-name>`)는 그대로 안 맞아 **커스텀 Keycloak SPI(ClientAuthenticator)** 구현이 필요한 경우가 많음.

> **업데이트 (2026-08-08):** 이 한계는 Keycloak의 범용 "Signed Jwt" client authenticator(자기서명 모델, `iss==sub==client_id` 전제) 기준 얘기였음. Keycloak이 26.4~26.6에 걸쳐 **K8s ServiceAccount를 1급 시민으로 지원하는 별도의 "Federated Client Authentication" 기능**을 냈고, 이건 애초에 `iss`/`sub`가 client_id와 다른 워크로드 아이덴티티를 전제로 설계된 것으로 보여 이 한계가 해소됐을 가능성이 높음. 아래 "Federated Client Authentication — 신규 네이티브 기능" 섹션 참고. 아직 실습으로 검증은 안 됨.

### 방법 B: Token Exchange — 권장

"외부 issuer가 서명한 JWT"를 "Keycloak이 발급한 access token"으로 교환. Vault K8s auth method와 유사한 패턴.

> 설정 단계, 버전(V1/V2) 관련 세부사항, 실습 진행 상황은 별도 문서로 분리: **[docs/keycloak-token-exchange.md](./keycloak-token-exchange.md)**

## 비교

| | 방법 A — 구식 (Signed JWT + JWKS, 커스텀 SPI) | 방법 B (Token Exchange) | 방법 A' — 신규 (Federated Client Authentication, 26.6+ GA) |
|---|---|---|---|
| 순정 Keycloak으로 가능? | 제한적 (커스텀 SPI 필요 가능성 높음) | 대부분 표준 기능으로 커버 | **가능 (네이티브, SPI 불필요)** |
| K8s SA 토큰의 `typ` 헤더 누락 영향 | 미확인 | **구조적 블로커 확인됨 (2026-08-08)** — 이 유스케이스 사용 불가 | 미확인 (다음 실습에서 검증 필요) |
| 기능 상태 | 커스텀 코드라 Keycloak 버전 무관 (SPI 깨질 리스크는 별개) | **영구 preview** (legacy V1, FGAP:v1도 preview) | **26.6+ 부터 GA(supported)** |
| 매핑 유연성 | 낮음 | namespace/SA별 세밀한 role 매핑 가능 | 미확인 (다음 실습에서 검증) |
| 운영 복잡도 | SPI 유지보수 부담 | Realm 설정 + 매핑 규칙 관리 + FGAP 정책 | Realm 설정(IDP 등록 + 인증 flow 바인딩), SPI/FGAP 불필요 |
| client_secret 필요 여부 | 불필요 (private key 대신 SA 토큰) | **필요** (client_secret으로 별도 클라이언트 인증) | **불필요** |

**결론 (2026-08-08 갱신, 이전 "방법 B 권장" 결론을 대체): 방법 B는 이 유스케이스에서 포기 (구조적 블로커 확인). 방법 A'(Federated Client Authentication, GA 기능)를 유력 후보로 재검토 중 — 단, 홈랩에서 실제로 K8s SA 토큰의 `typ` 헤더 문제를 안 겪는지 등 핵심 가정이 아직 실습으로 검증되지 않았음.**

## Federated Client Authentication — 신규 네이티브 기능 (2026-08-08 조사, 아직 미착수)

Keycloak 제품이 실제로 "Federated client authentication"이라는 이름을 붙여 낸 기능. 이 문서 제목의 넓은 개념과 이름이 겹치지만, 여기서부터는 **구체적인 Keycloak 빌트인 기능**을 가리킴.

### 버전 타임라인

| 버전 | 상태 |
|---|---|
| 26.4 (2025-09) | Federated Client Authentication 일반 기능 preview 도입 |
| 26.5 (2026-01) | K8s Service Account 지원 preview 추가 |
| **26.6 (2026-04)** | **K8s Service Account 포함 GA(supported)로 승격.** SPIFFE 클라이언트 인증은 여전히 preview(스펙 미확정) |
| 26.7.1 (2026-08-05, 최신) | 이후 릴리스, 세부 개선 포함 (아직 릴리스노트 상세 미확인) |

우리 홈랩은 **26.2.4** — 이 기능 자체가 없는 버전. 최소 26.6, 가급적 최신인 26.7.1로 업그레이드해야 시도 가능.

### 동작 방식 (요약, 검색 스니펫 기준 — 공식 문서 원문 정독 필요)

1. 지금 Token Exchange에서 한 것과 유사하게, **K8s 클러스터를 realm의 Identity Provider로 등록**해서 신뢰 관계를 맺음 (이 부분은 재사용 가능해 보임).
2. `client_credentials` 인증 흐름(authentication flow)에 **"Signed-JWT federated" execution**을 바인딩. (최신 버전은 기본 바인딩되어 있을 수 있고, 업그레이드 케이스는 flow를 복제해서 execution을 수동 추가해야 할 수 있음 — 확인 필요.)
3. 클라이언트는 K8s가 자동으로 마운트해주는 SA JWT를 **client_assertion**으로 사용해 `client_credentials` grant를 호출. `subject_token`/`token-exchange` grant 자체가 필요 없음.
4. Keycloak이 SA JWT 검증을 위해 `<issuer>/.well-known/openid-configuration` (OIDC discovery 문서)에 도달할 수 있어야 함 — 지금까지 쓰던 `jwksUrl` 직접 지정 방식과 다름. 우리 클러스터에서 이 엔드포인트가 outbound로 도달 가능한지, RBAC(`/openid/v1/jwks` 때와 같은 익명 접근 403 이슈)가 또 걸리는지 **다음 실습에서 확인 필요**.

### 열린 질문 (다음 실습에서 검증해야 할 것)

- [ ] `typ` 헤더 없는 K8s SA 토큰을 이 경로에서도 거부하는가, 아니면 이 기능은 K8s SA 토큰 형태를 전제로 설계되어 문제없이 통과하는가 — **오늘 겪은 블로커가 재발하는지가 이 피벗의 성패를 가르는 핵심 질문.**
- [ ] `.well-known/openid-configuration` 엔드포인트의 outbound 도달성/RBAC 확인 (K8s는 이 엔드포인트도 기본 제공하지만, 우리 환경에서 실제 노출/권한 상태는 미확인).
- [ ] namespace/SA별 role 매핑을 방법 B의 IDP Mapper만큼 세밀하게 할 수 있는지 (Hardcoded Role/Attribute mapper와 동등한 기능이 이 흐름에도 있는지).
- [ ] 관련 이슈 [#47067 "Support Pod-level workload identity"](https://github.com/keycloak/keycloak/issues/47067)가 아직 열려있다는 건 pod 단위 세밀한 identity 매핑이 완전히 성숙하지 않았을 가능성을 시사 — 확인 필요.
- [ ] 기존 realm(`k8s-token-exchange-poc`)에 남아있는 IDP/client/mapper 설정이 업그레이드 후에도 그대로 재사용 가능한지, 아니면 이 기능 전용으로 새로 정리해야 하는지.

### 출처

- [Federated client authentication - no more secrets - Keycloak](https://www.keycloak.org/2026/01/federated-client-authentication)
- [Keycloak 26.6.0 released](https://www.keycloak.org/2026/04/keycloak-2660-released)
- [Keycloak 26.7.1 released](https://www.keycloak.org/2026/08/keycloak-2671-released)
- [Issue #37600 — Experimental support for authenticating clients with Kubernetes Service Accounts](https://github.com/keycloak/keycloak/issues/37600)
- [Issue #44064 — Kubernetes service account default for federated client authentication](https://github.com/keycloak/keycloak/issues/44064)
- [Issue #47067 — Support Pod-level workload identity for Kubernetes federated JWT client authentication](https://github.com/keycloak/keycloak/issues/47067)

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

기술적으론 딱 맞는 조합이지만, 실무에서 도입을 미루게 되는 이유들 (버전/FGAP/온프렘 노출 등 세부 이유와 실습 진행 상황은 **[docs/keycloak-token-exchange.md](./keycloak-token-exchange.md)** 참고):

1. FGAP(Fine-Grained Admin Permission) 자체가 아직 무겁다 — 덜 검증된 기능 두 개(Token Exchange + FGAP)를 인증 경로에 동시에 얹는 리스크.
2. 복잡도가 없어지는 게 아니라 이동함 — 이미 Vault 등으로 secret 자동 로테이션이 돌아가는 조직이면, Token Exchange로 갈아타는 순간 IDP 등록·매퍼·FGAP 정책이라는 새 관리 대상이 생기는 것뿐.
3. 벤더 락인 — IDP 브로커링 + FGAP + Token Exchange 조합은 Keycloak 특화 구성.
4. 감사·팀 숙련도 비용 — "SA 토큰을 외부 IdP로 등록해 교환"하는 흐름은 생소해 매번 설명 필요.
5. **(2026-08-08 실습으로 확인) 순정 K8s SA 토큰은 애초에 이 경로를 통과 못 함** — `typ` 헤더가 없어서 Keycloak 24.0.3+의 강화된 subject_token 검증에 걸림. 위 1~4번은 "쓸 수는 있지만 도입을 망설이게 되는 이유"였던 반면, 이건 "이 조합으로는 애초에 안 됨"이라는 더 강한 결론. 위 4가지 이유 중 상당수(1, 3, 4번)를 회피하면서 typ 문제도 없을 가능성이 있는 대체 경로 → 아래 "Federated Client Authentication — 신규 네이티브 기능" 섹션 참고.