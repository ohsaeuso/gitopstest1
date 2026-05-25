plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.3"
    id("org.jetbrains.kotlin.kapt")
    application
}

version="1.0.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // Spring Security
    implementation("org.springframework.boot:spring-boot-starter-security")

    // OAuth2 Client (Keycloak 연동)
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // JWT 파싱 (토큰 디코딩용)
    implementation("org.springframework.security:spring-security-oauth2-jose")

    implementation(project(":utils"))

    // JUnit 5 테스트 의존성
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // kapt로 추가할 어노테이션 프로세서 예시 (필요한 라이브러리로 교체)
    implementation("org.mapstruct:mapstruct:1.5.5.Final")
    kapt("org.mapstruct:mapstruct-processor:1.5.5.Final")
}

kapt {
    // annotation processor의 오류 타입을 허용 (일부 라이브러리에서 필요)
    //correctErrorTypes = true
    // 빌드 캐시 사용
    //useBuildCache = true
}

application {
    mainClass = "org.example.app.AppKt"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.jar {
    archiveFileName = "app.jar"
}



