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
    implementation(libs.bundles.kotlinxEcosystem)
   /* testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")*/
}

version="1.0.0"

dependencies {

    // OAuth2 Client (Keycloak 연동)
    //implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // Keycloak SPI 의존성
    implementation("org.keycloak:keycloak-core:24.0.4")
    implementation("org.keycloak:keycloak-server-spi:24.0.4")
    implementation("org.keycloak:keycloak-server-spi-private:24.0.4")
    implementation("org.keycloak:keycloak-services:24.0.4")

    //implementation("org.jetbrains.kotlin:kotlin-stdlib")

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

tasks.bootJar{
    enabled = false
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17)) // Keycloak 서버 버전에 맞춤
    }
}

