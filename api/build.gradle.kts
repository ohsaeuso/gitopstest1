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
    // required at runtime: Spring Data JPA's constructor discovery introspects Kotlin metadata
    // on @Entity classes and throws NoClassDefFoundError without this on the classpath
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.3.0")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:testcontainers:2.0.2")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:oracle-free")
    testRuntimeOnly("com.oracle.database.jdbc:ojdbc11")
    runtimeOnly("com.h2database:h2")
}

application {
    mainClass = "org.example.api.AppKt"
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests (suffix IT)."
    group = "verification"
    include("**/*IT.class")
}

tasks.withType<Test>().configureEach {
    // activates src/test/resources/application-test.yaml as an overlay on top of the main
    // application.yaml, instead of a same-named file shadowing it outright
    systemProperty("spring.profiles.active", "test")
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