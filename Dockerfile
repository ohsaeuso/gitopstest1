# 🏗️ Build Stage
FROM gradle:8.14-jdk21 AS builder
WORKDIR /app
# 모든 파일 복사
COPY . .

# 실행 권한 설정 및 빌드
RUN chmod +x gradlew && \
    ./gradlew :app:build -x test --no-daemon && \
    ls -l /app/app/build/libs

# 🚀 Runtime Stage
FROM amazoncorretto:21

RUN apt-get update && apt-get install -y bash curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=builder /app/app/build/libs/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
