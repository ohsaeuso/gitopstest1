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

application {
    mainClass = "org.example.app.AppKt"
}

dependencies {
    // required for @RateLimiter/etc. to take effect: without aspectjweaver on the classpath,
    // Spring's AOP auto-proxying never activates and the annotations silently become no-ops
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.3.0")
    implementation("org.springframework.modulith:spring-modulith-api")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // brings HikariCP + DataSource/TransactionManager autoconfig for the test context only;
    // production has no persistence yet, so this stays test-scoped rather than main implementation
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:testcontainers:2.0.2")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:oracle-free")
    testRuntimeOnly("com.oracle.database.jdbc:ojdbc11")
    testRuntimeOnly("com.h2database:h2")
}

tasks.jar { enabled = true }
tasks.bootJar { enabled = false }