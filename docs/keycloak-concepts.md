# Keycloak 개념 정리 — Feature Flag / IDP / FGAP / Mapper

> [keycloak-token-exchange.md](./keycloak-token-exchange.md) 실습(K8s SA token → Keycloak token exchange)을 손으로 만지기 전에 잡아둔 배경 개념. 하나의 파이프라인으로 보면 됨: **능력 켜기(Feature Flag) → 신뢰 등록(IDP) → 권한 부여(FGAP) → 클레임 변환(Mapper)**.

## 1. Feature Flag — "이 기능 자체를 켤지 말지"

Keycloak은 기능마다 성숙도 등급(Stable / Preview / Experimental / Deprecated)이 있고, 기본은 **Stable만 켜짐**.

- 우리가 쓸 `token-exchange`(legacy V1)와 `admin-fine-grained-authz:v1`은 **Preview** → 명시적으로 켜야 함
- 관리자 콘솔 토글이 아니라 **서버 시작 옵션**인 이유: 켜지면 DB 스키마·REST 엔드포인트·핵심 인가 로직이 달라질 수 있어서, 런타임에 슬쩍 켰다 껐다 할 수 있는 종류가 아님
- Docker 컨테이너 기준: `KC_FEATURES=token-exchange,admin-fine-grained-authz:v1` env var → 컨테이너 재시작 필요

## 2. Identity Provider(IDP) 등록 — "이 외부 issuer를 신뢰 소스로 인정"

원래 IDP 등록은 "구글로 로그인" 같은 **브라우저 리다이렉트 기반 SSO(identity brokering)**용 기능. 우리는 그 리다이렉트 흐름은 전혀 안 쓰고, 그중 "issuer 문자열 + JWKS URL 등록" 부분만 재활용:

```bash
kcadm.sh create identity-provider/instances -r ecommerce \
  -s alias=k8s-cluster-home \
  -s providerId=oidc \
  -s config.issuer=https://kubernetes.default.svc.cluster.local \
  -s config.jwksUrl=https://192.168.0.21:6443/openid/v1/jwks \
  -s config.useJwksUrl=true
```

→ "이 alias로 부르는 이 issuer가 서명한 JWT는 이 JWKS로 검증한다"는 신뢰 레지스트리 엔트리 하나 만드는 것.

## 3. FGAP (Fine-Grained Admin Permissions) — "그 신뢰를 실제로 누가 써도 되는지"

### 왜 생겼나

기존엔 `realm-admin`, `manage-users` 같은 **뭉텅이 role**만 있어서, 필요한 것보다 항상 더 넓은 권한을 줄 수밖에 없고 그 role이 정확히 뭘 허용하는지도 불분명했음. FGAP는 이걸 **리소스 단위로 쪼개서 델리게이션**하기 위한 기능. ([keycloak.org 공식 블로그](https://www.keycloak.org/2025/05/fgap-kc-26-2))

### 구조 — Resource + Scope + Policy + Permission

Keycloak이 원래 일반 애플리케이션의 리소스 인가용으로 만든 Authorization Services(UMA 스타일) 엔진을, 자기 자신의 admin API에도 재활용하는 구조. (이 architecture 재사용 부분은 과거 지식 기반이고, 이번에 공식 문서로 100% 재확인은 못 함 — 필요시 재검증)

우리 케이스에 대입하면:

| 개념 | 우리 케이스 |
|---|---|
| **Resource** | 등록한 IDP 오브젝트 (`k8s-cluster-home`) |
| **Scope** | `token-exchange` (그 리소스에 대해 가능한 행위 종류) |
| **Policy** | "어떤 클라이언트면 허용인지" 규칙 (예: `ecommerce-api`만) |
| **Permission** | 위 셋을 묶는 것 — "이 Resource에 이 Scope를 이 Policy 조건일 때 허용" |

```
Identity Providers → k8s-cluster-home → Permissions → Enabled: ON
→ "token-exchange" permission → Policy에 대상 클라이언트 허용 추가
```

### V1 vs V2 (공식 확인됨)

| | FGAP V1 | FGAP V2 |
|---|---|---|
| 상태 | Preview (모든 버전) | 26.2+ 기본 활성화, GA |
| 마이그레이션 | V1 → V2 **자동 마이그레이션 없음** (권한 모델 자체가 근본적으로 다름 — Red Hat 공식 문서) | — |
| token-exchange 권한 지원 | ✅ | ❌ **의도적으로 제외** |

Keycloak 공식 문서:

> "Fine-grained admin permissions version 2 does not have support for token exchange permissions. **This is on purpose because token-exchange is conceptually not really an 'admin' permission.**"

풀어보면: V2는 "누가 어떤 클라이언트/그룹/유저를 **관리**할 수 있는가"(admin console 델리게이션)를 겨냥해 재설계된 거고, token-exchange는 "런타임 인증 흐름에서 어떤 교환이 허용되는가"라 성격이 다르다고 판단해서 V2 범위 밖에 의도적으로 뺀 것. → **우리처럼 token-exchange가 필요하면 V1을 쓸 수밖에 없음**, 그리고 V1은 "향후 릴리스에서 제거될 수도 있다"고 명시된 preview 상태. ([keycloak-token-exchange.md](./keycloak-token-exchange.md)의 리스크 정리 참고)

## 4. IDP Mapper — "검증된 외부 클레임을 우리 토큰의 의미로 번역"

서명 검증만 통과하면 "어떤 외부 identity가 인증됐다"는 것만 확인된 거지 그 자체로는 아무 권한도 아님. Mapper가 클레임 조건을 보고 실제 role/attribute로 변환:

```json
// 입력 (K8s SA 토큰 클레임)
{
  "sub": "system:serviceaccount:orders-ns:orders-sa",
  "kubernetes.io": { "namespace": "orders-ns", "serviceaccount": { "name": "orders-sa" } }
}
```

`kubernetes.io.namespace == orders-ns` 조건에 맞으면 → 결과 Keycloak 토큰에 `ROLE_ORDERS_SERVICE` 부여, 같은 방식의 Attribute/Hardcoded Role mapper로 구성.

---

## 파이프라인 요약

**1번 없으면** 애초에 기능이 없고 → **2번 없으면** K8s를 신뢰 못 하고 → **3번 없으면** 신뢰해도 아무도 못 쓰고 → **4번 없으면** 써도 아무 의미 없는 토큰만 나옴. 순서대로 막힌 문을 하나씩 여는 구조.

## 참고한 공식 문서

- [Fine-Grained Admin Permissions with Keycloak 26.2](https://www.keycloak.org/2025/05/fgap-kc-26-2)
- [Keycloak: Configuring and using token exchange](https://www.keycloak.org/securing-apps/token-exchange)
- Red Hat build of Keycloak 26.2 Upgrading Guide (검색 스니펫으로만 확인, 직접 fetch는 403)