plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    alias(libs.plugins.kotlinPluginSerialization)

}

dependencies {
    implementation(libs.bundles.kotlinxEcosystem)

}

version="1.0.0"

dependencies {


    // Keycloak SPI 의존성
    implementation("org.keycloak:keycloak-core:26.2.4")
    implementation("org.keycloak:keycloak-server-spi:26.2.4")
    implementation("org.keycloak:keycloak-server-spi-private:26.2.4")
    implementation("org.keycloak:keycloak-services:26.2.4")
    implementation("com.google.auto.service:auto-service:1.0.1")

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
    archiveFileName.set("dynamic-claim-mapper.jar")
}


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17)) // Keycloak 서버 버전에 맞춤
    }
}
