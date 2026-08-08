# Keycloak Token Exchange — K8s SA 토큰 연동 (실습 진행 문서)

> [keycloak-federated-client-auth.md](./keycloak-federated-client-auth.md)의 "방법 B"를 실제로 적용해보는 실습/설정 기록. **개인 온프렘 홈랩에서 PoC 진행 중이지만, 목적은 회사 도입 검토** — 아래 V1/preview 리스크는 회사에 올릴 결론에서 절대 누락하면 안 됨.
>
> 손 움직이기 전에 잡아둔 배경 개념(Feature Flag/IDP/FGAP/Mapper)은 **[keycloak-concepts.md](./keycloak-concepts.md)** 참고.

> ## ⛔ 상태 업데이트 (2026-08-08) — 이 경로(방법 B) 구조적으로 막힘, 방법 A 재검토로 피벗
>
> Mapper까지는 CLI 우회로 성공적으로 만들었으나, 실제 토큰 교환 호출에서 **K8s SA JWT에 `typ` 헤더가 없다는 이유로 구조적으로 막힘**을 확인함 (아래 "남은 실제 블로커" 4번 참고). 설정으로 우회 불가 — Keycloak 24.0.3+ 전 버전이 동일하게 막힘.
>
> 동시에 **Keycloak이 26.6부터 GA로 지원하는 신규 "Federated Client Authentication"(K8s Service Account 지원) 기능**을 발견 — 이게 바로 [keycloak-federated-client-auth.md](./keycloak-federated-client-auth.md)의 "방법 A"를 커스텀 SPI 없이 네이티브로 구현한 것으로 보임. 자세한 내용과 다음 단계는 그 문서의 "업데이트 (2026-08-08)" 섹션 참고. **이 문서(방법 B)의 실습은 여기서 일시 중단**, 홈랩 Keycloak을 26.7.1로 업그레이드한 뒤 방법 A(신규 기능)로 재시도할 예정.

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

**Keycloak 컨테이너 세부 (2026-08-03, `docker inspect`로 확인):**

- **docker-compose로 관리** — `/home/ubuntu/keycloak/docker-compose.yaml`, project `keycloak`. 설정 변경은 `docker run` 재실행이 아니라 이 compose 파일을 고치고 `docker compose up -d`로 재생성해야 함 (그래야 `restart: unless-stopped` 등 기존 정책이 유지됨).
- **비root 유저**(`User: 1000`)로 실행 — 볼륨 마운트하는 파일(CA 인증서 등)은 uid 1000이 읽을 수 있는 권한(예: `chmod 644`)이어야 함.
- **DB는 Oracle이지만 원격** — `KC_DB_URL=jdbc:oracle:thin:@//100.69.90.92:1521/freepdb1`, `extra_hosts: worker1:100.89.245.10` 둘 다 Tailscale 대역(`100.x`) IP. 즉 Keycloak 앱은 로컬(`ubuntu-home-1`)에서 뜨지만 DB 백엔드는 Tailscale 너머 다른 호스트에 있음 — apiserver(LAN `192.168.0.21`) 도달성과는 별개 경로/신뢰 설정이라 서로 영향 없음.
- **브리지 네트워크**(`keycloak_default`, host 네트워크 아님) — 컨테이너 IP는 `172.18.0.x`. apiserver로 나가는 아웃바운드는 Docker NAT를 거치지만 실질적으로 문제 없이 통과함 (JWKS 도달성 검증 완료, 아래 참고).
- 기존 바인드 마운트 3개는 커스텀 provider jar들 (`ojdbc17.jar`, `dynamic-claim-mapper.jar`, `kotlin-stdlib-2.2.21.jar`) — CA truststore는 이번에 새로 추가.

## 남은 실제 블로커

~~1. TLS 신뢰~~, ~~2. Feature flag 활성화~~ — 2026-08-03 해소. `docker-compose.yaml`(`/home/ubuntu/keycloak/`)에 `KC_TRUSTSTORE_PATHS=/opt/keycloak/conf/truststore`(디렉터리 마운트, `./truststore/ca.crt` ← `/etc/kubernetes/pki/ca.crt`, uid 1000 읽기 위해 `chmod 644`)와 `KC_FEATURES=token-exchange,admin-fine-grained-authz:v1` 추가 후 `docker compose up -d`. 기동 로그로 확인 완료:

```
Preview features enabled: admin-fine-grained-authz:v1, token-exchange:v1
Found the following truststore files under directories specified in the truststore paths [/opt/keycloak/conf/truststore/ca.crt]
```

~~3. apiserver JWKS 도달성~~ — 2026-08-03 해소, 단 진행 중 새 블로커 발견:

Keycloak 컨테이너(uid 1000)엔 `curl`이 없어 (미니멀 base 이미지) 같은 `keycloak_default` 브리지 네트워크에 `curlimages/curl` 임시 컨테이너를 붙여 검증:

```bash
docker run --rm --network keycloak_default -v ~/keycloak/truststore/ca.crt:/ca.crt:ro \
  curlimages/curl -sv --cacert /ca.crt https://192.168.0.21:6443/openid/v1/jwks
```

TLS(`--cacert ca.crt`)는 바로 통과했지만 `403 Forbidden: system:anonymous cannot get path "/openid/v1/jwks"` 발생.

원인: K8s는 `/openid/v1/jwks`용 `system:service-account-issuer-discovery` ClusterRole을 기본 제공하지만, 기본 ClusterRoleBinding은 `system:serviceaccounts` 그룹에만 걸려 있고 **익명(`system:unauthenticated`)은 기본적으로 제외**됨 (`kubectl describe clusterrolebinding system:service-account-issuer-discovery`로 확인). 이 기본 바인딩엔 `rbac.authorization.kubernetes.io/autoupdate: true`가 붙어 있어 직접 편집하면 reconciler가 되돌리므로, **별도의 새 ClusterRoleBinding**으로 익명 접근을 추가하는 게 맞는 방법 (K8s 공식 문서가 권장하는 패턴이기도 함):

```bash
kubectl create clusterrolebinding service-account-issuer-discovery-anonymous \
  --clusterrole=system:service-account-issuer-discovery \
  --group=system:unauthenticated
```

**보안 트레이드오프 검토(회사 도입 검토 시 반드시 언급):** `system:unauthenticated`에 뭔가 바인딩하는 건 일반적으론 경계 대상 패턴이지만, 이 ClusterRole은 GET 2개 non-resource URL만 허용하고 응답 내용도 공개 목적의 서명 검증용 공개키(JWKS)라 비밀 유출 리스크는 낮음. 다만 "익명 접근 허용"이라는 패턴 자체의 확장 리스크, 그리고 apiserver가 홈랩보다 넓게 노출된 환경(사내 온프렘 등)일 때는 노출 표면이 커진다는 점은 인지 필요. 대안으로 JWKS를 한 번 받아 Keycloak IDP에 정적 등록(apiserver RBAC를 전혀 안 건드림, 대신 키 로테이션 시 수동 갱신 필요)하는 방법도 있음 — 이번엔 공식 권장 방식(익명 바인딩)으로 진행하기로 결정.

적용 후 재검증 완료:
```
HTTP/2 200
content-type: application/jwk-set+json
{"keys":[{"use":"sig","kty":"RSA","kid":"1sPcP8rdyvGmFniWxZhyiSsnNo4460SdruktQxsCigo", ...}]}
```

**4. `typ` 헤더 누락 — 구조적 블로커, 설정으로 해결 불가 (2026-08-08, 미해결·경로 포기)**

client_secret 인증, IDP validateSignature, mapper까지 다 맞춘 뒤 실제 토큰 교환(`grant_type=...token-exchange`) 호출 시 계속 다음 에러:

```json
{ "error": "invalid_token", "error_description": "token type not supported" }
```

`requested_token_type` 파라미터를 명시해도 동일 — subject_token_type/requested_token_type 파라미터 문제가 아니라 **subject_token(JWT) 자체를 파싱하는 단계**에서 거부되는 것으로 확인. JWT 헤더를 직접 디코드해보면:

```json
{ "alg": "RS256", "kid": "1sPcP8rdyvGmFniWxZhyiSsnNo4460SdruktQxsCigo" }
```

**`typ` 클레임이 아예 없음.** 원인(웹 검색으로 확인, 아래 출처):

- Keycloak **24.0.3부터** RFC 9068 기준으로 token exchange의 subject_token에 `typ` 헤더 클레임을 요구하도록 검증이 강화됨. 22.0.4 이하에서는 없어도 통과했음.
- **K8s apiserver의 내장 ServiceAccount 토큰 발급기는 애초에 `typ` 헤더를 넣지 않음** (`kubectl create token`으로 발급되는 모든 토큰이 동일). 클러스터 설정으로 바꿀 수 있는 옵션이 아님 — 커스텀 토큰 발급기(별도 signer)를 붙이지 않는 한 K8s 쪽에서 고칠 수 없음.
- 즉 **24.0.3 이후 모든 Keycloak 버전(26.2.4, 26.7.1 포함)에서, 순정 K8s SA 토큰을 그대로 legacy V1 token exchange의 subject_token으로 쓰는 것 자체가 막혀 있음.** 우리 환경만의 설정 문제가 아니라 두 시스템 조합 자체의 구조적 비호환.

**출처:**
- ["token exchange" feature in Red Hat build of Keycloak not working when JWT token doesn't contain a "typ" header claim](https://access.redhat.com/solutions/7115095)
- [EntraID/AzurAD Application Token-Exchange fails due to incorrect supported token type check · Issue #37734](https://github.com/keycloak/keycloak/issues/37734)

**결정 (2026-08-08):** 이 블로커를 우회하는 대신, 방법 B(Token Exchange) 자체를 이 유스케이스에서 포기하고 방법 A의 네이티브 구현(신규 "Federated Client Authentication" 기능, Keycloak 26.6+ GA)으로 피벗하기로 함. 자세한 내용은 [keycloak-federated-client-auth.md](./keycloak-federated-client-auth.md) 참고. 이 문서에 남은 "설정 단계"/"TODO"는 **참고용으로 보존**하되 더 이상 실습을 진행하지 않음.

## 설정 단계 (V1 기준, 여전히 유효한 절차 — 단, 위 4번 블로커로 인해 현재 이 유스케이스엔 사용 불가. 기록 보존 목적)

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
kcadm.sh create identity-provider/instances -r k8s-token-exchange-poc \
  -s alias=k8s-cluster-home \
  -s providerId=oidc \
  -s enabled=true \
  -s config.issuer=https://kubernetes.default.svc.cluster.local \
  -s config.jwksUrl=https://192.168.0.21:6443/openid/v1/jwks \
  -s config.useJwksUrl=true \
  -s config.validateSignature=true \
  -s config.clientAuthentication=none
```

> **정정 (2026-08-08):** 원래 이 명령엔 `config.validateSignature=true`가 빠져 있었음. `jwksUrl`/`useJwksUrl`만으로는 서명 검증이 실제로 켜지지 않아 subject_token 검증 단계에서 `invalid_token`("token type not supported" 이전 단계 에러)이 발생했음 — 실습 중 `kcadm.sh update identity-provider/instances/k8s-cluster-home -r k8s-token-exchange-poc -s config.validateSignature=true`로 사후 적용해서 확인함. 위 명령은 정정 반영된 버전.

**3. 토큰 교환을 실제로 호출할 confidential 클라이언트 생성** (2026-08-06 생성)

```bash
docker exec -it keycloak /opt/keycloak/bin/kcadm.sh create clients -r k8s-token-exchange-poc \
  -s clientId=token-exchange-client \
  -s enabled=true \
  -s publicClient=false \
  -s serviceAccountsEnabled=true \
  -s standardFlowEnabled=false \
  -s directAccessGrantsEnabled=false
```

시크릿 확인:

```bash
docker exec -it keycloak /opt/keycloak/bin/kcadm.sh get clients -r k8s-token-exchange-poc \
  -q clientId=token-exchange-client -F id

docker exec -it keycloak /opt/keycloak/bin/kcadm.sh get clients/<위에서_나온_id>/client-secret \
  -r k8s-token-exchange-poc
```

**4. 해당 IDP에 Token Exchange 권한 부여** (FGAP:v1 활성화 후, `token-exchange-client`를 허용 대상으로 지정)

```
Identity Providers → k8s-cluster-home → Permissions → Enabled: ON
→ "token-exchange" permission → Policy에 token-exchange-client 허용 추가
```

**5. IDP Mapper로 SA 클레임 → role/attribute 매핑**

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

> **Admin Console UI 버그 우회 (2026-08-08):** Console의 "Add Identity Provider Mapper" 화면에서 Hardcoded Role 매퍼의 Role 검색창에 미리 만들어둔 realm role(`poc-workload`)이 안 뜨는 버그 발견 (role 자체는 `kcadm.sh get roles`로 realm에 정상 존재 확인됨 — `clientRole: false`, 올바른 `containerId`). 필터 토글도 문제 아니었음. 원인 특정은 못했으나, Console UI를 완전히 우회해서 REST/CLI로 mapper를 직접 생성해 해결:
>
> ```bash
> docker exec -it keycloak /opt/keycloak/bin/kcadm.sh create identity-provider/instances/k8s-cluster-home/mappers -r k8s-token-exchange-poc \
>   -s name=hardcoded-role-mapper \
>   -s identityProviderAlias=k8s-cluster-home \
>   -s identityProviderMapper=oidc-hardcoded-role-idp-mapper \
>   -s config.role=poc-workload
> ```
>
> mapper 생성 자체는 성공(`Created new mapper with id '...'`). Console UI에 실제로 나타나는지까지는 확인 안 하고 다음 블로커(아래 4번)로 넘어감.

**6. 클라이언트 호출**

```
POST /realms/k8s-token-exchange-poc/protocol/openid-connect/token
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
client_id=token-exchange-client
client_secret=<3번에서 확인한 시크릿>
subject_token=<K8s SA JWT>
subject_token_type=urn:ietf:params:oauth:token-type:jwt
subject_issuer=k8s-cluster-home
```

## TODO / 다음 단계

- [x] Keycloak 버전 확인 → 26.2.4 (단, 이건 V1 preview 여부와 무관 — 위 정정 참고)
- [x] 클러스터 종류 확인 → 온프렘, kubeadm 3노드
- [x] issuer/JWKS 접근성 확인 → 로컬(같은 호스트)에서 정상 응답, Keycloak도 같은 호스트라 네트워크 도달성 문제 없음
- [x] `docker inspect keycloak`로 기존 마운트/env 확인 → docker-compose 관리, uid 1000, 기존 바인드 마운트 3개(providers jar) 확인
- [x] kubeadm CA 인증서(`/etc/kubernetes/pki/ca.crt`) 확보 및 Keycloak 컨테이너에 마운트 + `KC_TRUSTSTORE_PATHS` 설정 (2026-08-03)
- [x] `KC_FEATURES=token-exchange,admin-fine-grained-authz:v1` 적용 후 컨테이너 재시작 (2026-08-03)
- [x] apiserver JWKS 엔드포인트 outbound 도달성 확인 → TLS는 통과했으나 RBAC 403 발견, `system:service-account-issuer-discovery`를 `system:unauthenticated`에 추가 바인딩 후 200 확인 (2026-08-03)
- [x] realm 확인 (2026-08-06) → 문서에 있던 `ecommerce` 가정은 틀림. 실제 Admin Console 기준 기존 realm은 `master`(Keycloak 기본), `realm1`(이전 다른 실습용, 이번 PoC와 무관) 두 개뿐이었음. 이번 PoC 전용으로 Admin Console에서 새 realm `k8s-token-exchange-poc` 생성 완료. 위 "설정 단계"의 `-r ecommerce` / `/realms/ecommerce/...` 예시는 모두 `k8s-token-exchange-poc`로 정정함.
- [x] IDP 등록 (`k8s-cluster-home`, realm=`k8s-token-exchange-poc`) — kcadm.sh로 생성 완료 (2026-08-06)
- [x] confidential 클라이언트 생성 (`token-exchange-client`, service account 활성화) (2026-08-06)
- [x] FGAP token-exchange 권한 부여 — Console에서 Identity Providers → k8s-cluster-home → Permissions → Enabled ON, `Client` 타입 정책 `allow-token-exchange-client` 생성 후 `token-exchange.permission.idp` / `token-exchange.permission.client` 두 permission 모두에 바인딩 (2026-08-06)
- [x] 테스트용 K8s 리소스 준비 (2026-08-06) — namespace `token-exchange-poc`, SA `keycloak-test-sa`, `kubectl create token keycloak-test-sa -n token-exchange-poc --audience=keycloak --duration=1h`로 테스트용 JWT 발급 (pod 배포 없이 K8s 1.24+ 기능으로 직접 발급, 값은 기록 안 함)
- [x] realm role `poc-workload` 생성 (kcadm.sh, 2026-08-06) — `get roles -r k8s-token-exchange-poc -F name`로 존재 확인됨
- [x] **블로커 (2026-08-06) 해결 (2026-08-08)**: Console의 IDP Mapper 화면에서 `poc-workload` role이 검색 안 되던 문제 — 원인 미특정, Console UI를 우회해서 `kcadm.sh create identity-provider/instances/k8s-cluster-home/mappers`로 mapper 직접 생성해서 해결 (위 "설정 단계" 5번 참고).
- [x] `config.validateSignature=true` 누락 발견 및 적용 (2026-08-08) — 원래 IDP 등록 명령에 빠져 있었음 (위 "설정 단계" 2번 정정 참고).
- [x] 실제 토큰 교환 호출 테스트 (2026-08-08) → **구조적 블로커로 실패, 경로 포기**: K8s SA JWT에 `typ` 헤더가 없어서 Keycloak 24.0.3+의 강화된 RFC 9068 검증에 걸림 (위 "남은 실제 블로커" 4번 참고). 설정으로 해결 불가능한 것으로 결론.
- [x] 회사에 실제로 Vault/Secrets Manager가 있는지 확인하는 항목은 → 아래 대체 경로(Federated Client Auth)에서는 **애초에 secret 자체가 없어져서** 이 비교축 자체가 무의미해질 가능성 있음. 대체 경로가 실제로 검증되면 이 TODO는 폐기 예정, 아직은 보류.
- [x] PoC 결과를 회사에 보고할 때 "legacy V1(preview) + FGAP:v1(제거 가능성 명시됨)" 의존성을 결론에 반드시 포함 → **이 항목은 방법 B 자체를 포기했으므로 "왜 포기했는지"의 근거로 보고서에 남기면 됨** (preview 리스크 + typ 헤더 구조적 비호환 둘 다).
- [ ] **다음 단계 (2026-08-08 결정)**: 이 문서의 실습은 여기서 중단. 홈랩 Keycloak을 26.2.4 → 26.7.1로 업그레이드한 뒤, [keycloak-federated-client-auth.md](./keycloak-federated-client-auth.md)의 "Federated Client Authentication (신규 네이티브 기능)" 섹션 기준으로 방법 A를 처음부터 다시 시도. 업그레이드 자체가 기존 realm/client/IDP 설정에 미치는 영향은 사전 확인 필요 (아직 미확인 — 사용자 확인 대기 중이었음).