# Keycloak Token Exchange — K8s SA 토큰 연동 (실습 진행 문서)

> [keycloak-federated-client-auth.md](./keycloak-federated-client-auth.md)의 "방법 B"를 실제로 적용해보는 실습/설정 기록. **개인 온프렘 홈랩에서 PoC 진행 중이지만, 목적은 회사 도입 검토** — 아래 V1/preview 리스크는 회사에 올릴 결론에서 절대 누락하면 안 됨.
>
> 손 움직이기 전에 잡아둔 배경 개념(Feature Flag/IDP/FGAP/Mapper)은 **[keycloak-concepts.md](./keycloak-concepts.md)** 참고.

## 참고한 공식 문서

- [Kubernetes: Configure Service Accounts for Pods](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/) — projected SA token, `ServiceAccountIssuerDiscovery`
- [Keycloak: Configuring and using token exchange](https://www.keycloak.org/securing-apps/token-exchange) — Standard Token Exchange V2 스펙, 요청 파라미터, V1/V2 비교
- [Keycloak: JWT Authorization Grant](https://www.keycloak.org/securing-apps/jwt-authorization-grant) — RFC 7523 기반 신규 대체 그랜트
- Red Hat build of Keycloak 26.2 "Securing Applications and Services Guide" — 직접 fetch는 403으로 막혀 검색 스니펫으로만 확인 (신뢰도 낮음, 필요시 재확인)
- Keycloak 릴리스노트 검색 (26.2/26.5/26.6/26.7) — 기능별 preview→GA 시점 확인용

### K8s 쪽 공식 문서에서 확인한 캐치

Kubernetes 공식 문서는 외부에서 OIDC discovery로 SA 토큰을 검증하려면 **issuer URL이 HTTPS + 공개 접근 가능**해야 한다고 명시한다 (`https://kubernetes.default.svc.cluster.local`처럼 클러스터 내부 DNS 이름은 discovery 용도로는 부적합, `https://kubernetes.example.com`처럼 진짜 공인 도메인이어야 함).

→ 우리 클러스터의 issuer가 딱 이 "내부 전용" 케이스라 원칙적으로는 문제. 다만 우리가 쓰려는 방식은 Keycloak IDP 설정에서 `config.jwksUrl`을 **직접 지정**(`useJwksUrl=true`)하는 것이라, Keycloak이 `issuer` 값으로 discovery 문서를 fetch하러 가지 않고 그냥 `iss` claim과 문자열 비교만 함 — 그래서 실질적으로 discovery 스펙 미준수는 우회됨. (단, 이건 "우리 설정 방식이 우연히 이 문제를 피해가는 것"이지 K8s가 권장하는 정석 구성은 아니라는 점은 인지하고 있어야 함.)

### Token Exchange V2 요청 포맷 (공식 문서 기준, 참고용 — 우리는 V1 사용)

```
POST /realms/{realm}/protocol/openid-connect/token
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
subject_token=<token>
subject_token_type=urn:ietf:params:oauth:token-type:access_token   # V2는 이 타입만 지원
requested_token_type=urn:ietf:params:oauth:token-type:access_token # 선택, 기본값
audience=<target-client-id>                                       # V2 internal-internal용, subject_issuer 없음
```

V1(우리가 쓸 것)은 `subject_token_type=...:jwt` + `subject_issuer=<idp-alias>` 조합을 추가로 지원한다는 점이 핵심 차이 (아래 "설정 단계"의 5번 참고).

## ⚠️ V1 vs V2 — 처음에 잘못 알고 있었던 것 (2026-08-01, 공식 문서 확인 후 정정)

원래 "Keycloak 26.2+ = Standard Token Exchange V2가 GA니까 버전 문제 해결"이라고 생각했는데 틀렸다.

| | Standard Token Exchange **V2** | Legacy Token Exchange **V1** |
|---|---|---|
| 상태 | 26.2+ 기본 활성화, GA | 모든 버전에서 여전히 **preview** (26.7도 동일) |
| 지원 범위 | **internal-to-internal만** — 같은 realm 안 client A 토큰 → client B용 토큰 | **external-to-internal 포함** — 외부 IDP가 서명한 JWT(`subject_issuer`)도 교환 가능 |
| 우리가 필요한 것(K8s SA JWT → Keycloak 토큰) | ❌ 지원 안 함 (`subject_issuer` 파라미터 자체가 V2에 없음) | ✅ 이게 우리가 실제로 써야 하는 것 |
| FGAP 필요 여부 | 불필요 | **FGAP:v1 필수** (v2는 의도적으로 token-exchange 권한 미지원) |

**결론: Keycloak 버전을 올린다고 이 기능이 GA가 되는 게 아니다.** 우리가 쓸 경로는 지금도 preview 상태인 legacy V1 + FGAP:v1이고, 업스트림 문서는 FGAP:v1이 "향후 릴리스에서 제거될 수도 있다"고 명시하고 있다.

**회사 도입 검토 관점에서 이 리스크의 무게:** 이건 "언젠가 재설정하면 그만"인 수준이 아니라, 프로덕션에 이 경로를 올린 상태에서 보안 패치 때문에 어쩔 수 없이 Keycloak을 마이너 업그레이드해야 하는 시점에 인증 경로 자체가 깨질 수 있다는 뜻 — 그리고 preview 기능이라 공식 지원/픽스 대상도 아님. 홈랩 PoC는 이 리스크를 감수하고 계속 진행하지만, **회사에 "채택하자"고 결론 내릴 때는 이 preview/FGAP:v1 의존성을 반드시 명시**해야 함 (예: "GA될 때까지 도입 보류" 또는 "preview 리스크를 감수할 만한 워크로드에만 한정 적용" 같은 조건부 권고로).

**참고 — JWT Authorization Grant (RFC 7523, `grant_type=...jwt-bearer`):** 26.5(preview)/26.6(GA)에 추가된 신규 대체재. 외부 JWT를 **미리 연결된 Keycloak 사용자 계정**에 매핑하는 모델이라 "네임스페이스/SA → role" 같은 워크로드(머신) 아이덴티티 매핑에는 맞지 않고, 우리 Keycloak(26.2.4)엔 애초에 없는 기능이라 지금은 선택지가 아님. 나중에 업그레이드해도 이 유스케이스엔 legacy V1가 더 적합해 보임 (사용자 계정 개념이 없어도 됨).

## 진행 상황 (홈랩 실측)

**클러스터**: kubeadm, 3노드 (`ubuntu-home-1/2/3`), K8s v1.35.3, 온프렘.

```
issuer:   https://kubernetes.default.svc.cluster.local
jwks_uri: https://192.168.0.21:6443/openid/v1/jwks
```

- `issuer`는 클러스터 내부 DNS 이름 그대로 — discovery 재요청 없이 `config.jwksUrl`을 직접 지정하는 방식(`useJwksUrl=true`)을 쓸 거라 네트워크로 resolve될 필요는 없고, `iss` claim 비교용 문자열로만 등록하면 됨.
- `jwks_uri`는 control-plane 사설 IP(`192.168.0.21`) + 6443 포트.

**Keycloak**: `192.168.0.21`(control-plane 노드)에 Docker로 직접 구동 중 (`quay.io/keycloak/keycloak:26.2.4`, 포트 8070 → nginx 리버스 프록시가 인바운드 TLS 종료). **K8s apiserver와 같은 호스트**라 네트워크 도달성은 문제 없음 (방화벽/포트포워딩 불필요).

> nginx는 Keycloak의 **인바운드** 트래픽(브라우저 → Keycloak)에만 관여. Keycloak → K8s apiserver로 나가는 **아웃바운드** JWKS fetch와는 무관 — 서로 다른 TLS 연결, 다른 신뢰 설정(`KC_HTTPS_CERTIFICATE_FILE` vs `KC_TRUSTSTORE_PATHS`)이라 영향 없음.

## 남은 실제 블로커

1. **TLS 신뢰** — Keycloak이 apiserver(6443)의 self-signed 인증서(kubeadm CA)를 신뢰하도록 `KC_TRUSTSTORE_PATHS`에 CA 인증서(`/etc/kubernetes/pki/ca.crt`)를 추가해야 함. 컨테이너 재생성(볼륨 마운트 추가) 필요 — 현재 `docker inspect keycloak`으로 기존 마운트/env 확인 대기 중.
2. **Feature flag 활성화** — legacy V1 + FGAP:v1은 기본 꺼져 있음. Keycloak 컨테이너에 다음 env var 추가 후 재시작 필요:
   ```
   KC_FEATURES=token-exchange,admin-fine-grained-authz:v1
   ```
   (v2가 아니라 **v1** 명시 — v2는 token-exchange 권한 자체가 없음)

## 설정 단계 (V1 기준, 여전히 유효한 절차)

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

**2. Keycloak — K8s 클러스터를 Identity Provider(OIDC)로 등록**

```bash
kcadm.sh create identity-provider/instances -r ecommerce \
  -s alias=k8s-cluster-home \
  -s providerId=oidc \
  -s enabled=true \
  -s config.issuer=https://kubernetes.default.svc.cluster.local \
  -s config.jwksUrl=https://192.168.0.21:6443/openid/v1/jwks \
  -s config.useJwksUrl=true \
  -s config.clientAuthentication=none
```

**3. 해당 IDP에 Token Exchange 권한 부여** (FGAP:v1 활성화 후)

```
Identity Providers → k8s-cluster-home → Permissions → Enabled: ON
→ "token-exchange" permission → Policy에 대상 클라이언트 허용 추가
```

**4. IDP Mapper로 SA 클레임 → role/attribute 매핑**

SA 토큰 클레임 예시:

```json
{
  "iss": "https://kubernetes.default.svc.cluster.local",
  "sub": "system:serviceaccount:<ns>:<sa-name>",
  "kubernetes.io": {
    "namespace": "<ns>",
    "serviceaccount": { "name": "<sa-name>" }
  },
  "aud": ["keycloak"]
}
```

특정 네임스페이스/SA만 원하는 role을 받도록 조건부 Attribute/Hardcoded Role mapper 구성.

**5. 클라이언트 호출**

```
POST /realms/ecommerce/protocol/openid-connect/token
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
client_id=<client-id>
subject_token=<K8s SA JWT>
subject_token_type=urn:ietf:params:oauth:token-type:jwt
subject_issuer=k8s-cluster-home
```

## TODO / 다음 단계

- [x] Keycloak 버전 확인 → 26.2.4 (단, 이건 V1 preview 여부와 무관 — 위 정정 참고)
- [x] 클러스터 종류 확인 → 온프렘, kubeadm 3노드
- [x] issuer/JWKS 접근성 확인 → 로컬(같은 호스트)에서 정상 응답, Keycloak도 같은 호스트라 네트워크 도달성 문제 없음
- [ ] `docker inspect keycloak`로 기존 마운트/env 확인 (CA 인증서 추가 방법 결정용)
- [ ] kubeadm CA 인증서(`/etc/kubernetes/pki/ca.crt`) 확보 및 Keycloak 컨테이너에 마운트 + `KC_TRUSTSTORE_PATHS` 설정
- [ ] `KC_FEATURES=token-exchange,admin-fine-grained-authz:v1` 적용 후 컨테이너 재시작
- [ ] IDP 등록 (`k8s-cluster-home`) → FGAP token-exchange 권한 부여 → 매퍼 구성 → 실제 토큰 교환 호출 테스트
- [ ] 회사에 실제로 Vault/Secrets Manager가 있는지 확인 — 있다면 `client_secret` 자동 로테이션이 이미 더 검증된 대안일 수 있음 (홈랩 PoC라고 이 항목을 건너뛰면 안 됨, 회사 도입 검토의 핵심 비교 축)
- [ ] PoC 결과를 회사에 보고할 때 "legacy V1(preview) + FGAP:v1(제거 가능성 명시됨)" 의존성을 결론에 반드시 포함