# 🏗️ Build Stage
FROM gradle:8.14-jdk21 AS builder
WORKDIR /app
# 모든 파일 복사
COPY . .

# 실행 권한 설정 및 빌드
RUN chmod +x gradlew && \
    ./gradlew :app:build -x test --no-daemon && \
    ls -l /app/app/build/libs

# 런타임 스테이지
FROM amazonlinux:2023

# Java + bash + curl 설치
RUN dnf install -y java-21-amazon-corretto-headless bash curl && \
    dnf clean all

WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=builder /app/app/build/libs/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
