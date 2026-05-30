# 빌드 스테이지
FROM gradle:8.14-jdk21 AS builder
WORKDIR /app
COPY . .
RUN chmod +x gradlew && \
    ./gradlew :app:build -x test --no-daemon && \
    ls -l /app/app/build/libs

# 런타임 스테이지
FROM ubuntu:22.04

RUN apt-get update && \
    apt-get install -y bash curl openjdk-21-jre-headless openssl dnsutils && \
    apt-get clean

WORKDIR /app

# 🔑 1. 인증서 체인 추출 (self-signed 포함)
RUN echo | openssl s_client -showcerts -connect keycloak.external.com:443 -servername keycloak.external.com \
    | sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' > fullchain.crt

# 🔑 2. truststore에 등록
RUN keytool -importcert -trustcacerts -alias "keycloak" \
    -file "fullchain.crt" \
    -keystore "$JAVA_HOME/lib/security/cacerts" \
    -storepass changeit -noprompt

# 🔍 3. 등록 확인
RUN keytool -list -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit | grep keycloak || (echo "❌ 인증서 등록 실패" && exit 1)

# 🔍 4. OIDC 엔드포인트 SSL 테스트
RUN curl -vk https://keycloak.external.com/realms/realm1/.well-known/openid-configuration || (echo "❌ SSL handshake 실패" && exit 1)

# 빌드된 JAR 복사
COPY --from=builder /app/app/build/libs/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]