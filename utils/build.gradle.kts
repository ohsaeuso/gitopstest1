plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    alias(libs.plugins.kotlinPluginSerialization)

    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.3"
}

dependencies {
    // Apply the kotlinx bundle of dependencies from the version catalog (`gradle/libs.versions.toml`).
    implementation(libs.bundles.kotlinxEcosystem)
    testImplementation(kotlin("test"))
}

version="1.0.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // Spring Security
    //implementation("org.springframework.boot:spring-boot-starter-security")

    // OAuth2 Client (Keycloak 연동)
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // JWT 파싱 (토큰 디코딩용)
    //implementation("org.springframework.security:spring-security-oauth2-jose")

    // Keycloak SPI 의존성
    implementation("org.keycloak:keycloak-core:24.0.4")
    implementation("org.keycloak:keycloak-server-spi:24.0.4")
    implementation("org.keycloak:keycloak-server-spi-private:24.0.4")
    implementation("org.keycloak:keycloak-services:24.0.4")

    implementation("org.jetbrains.kotlin:kotlin-stdlib")

}



tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "Keycloak Dynamic Claim Mapper",
            "Implementation-Version" to version
        )
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17)) // Keycloak 서버 버전에 맞춤
    }
}

