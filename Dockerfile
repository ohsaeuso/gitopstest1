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
    apt-get install -y bash curl openjdk-21-jdk-headless openssl dnsutils && \
    apt-get clean

WORKDIR /app

# crt 파일 준비 (이미 추출한 nginx.crt를 COPY)
COPY --from=builder /app/app/nginx.crt nginx.crt

# truststore 등록
RUN keytool -importcert -trustcacerts -alias "nginx" \
    -file "nginx.crt" \
    -keystore "/etc/ssl/certs/java/cacerts" \
    -storepass changeit -noprompt

# 등록 확인
RUN keytool -list -keystore /etc/ssl/certs/java/cacerts -storepass changeit | grep nginx || (echo "❌ 인증서 등록 실패" && exit 1)

# SSL handshake 테스트
RUN curl -vk https://keycloak.external.com/realms/realm1/.well-known/openid-configuration || (echo "❌ SSL handshake 실패" && exit 1)


# 빌드된 JAR 복사
COPY --from=builder /app/app/build/libs/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]