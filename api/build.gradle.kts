plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.spring") version "2.2.0"
    application
}

version = "1.0.0"

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:1.4.3")
    }
}

dependencies {
    implementation(project(":app"))
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.3.0")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation(project(":utils"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:testcontainers:2.0.2")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:oracle-free")
    testRuntimeOnly("com.oracle.database.jdbc:ojdbc11")
    runtimeOnly("com.h2database:h2")
}

application {
    mainClass = "org.example.app.AppKt"
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests (suffix IT)."
    group = "verification"
    include("**/*IT.class")
}

tasks.test {
    exclude("**/*IT.class")
}

tasks.check {
    dependsOn(integrationTest)
}

tasks.jar {
    enabled = false
}
tasks.bootJar {
    archiveFileName = "api.jar"
    enabled = true
}