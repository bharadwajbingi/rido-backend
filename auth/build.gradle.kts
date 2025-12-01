plugins {
    id("org.springframework.boot") version "3.2.1"
    id("io.spring.dependency-management") version "1.1.3"
    java
}

group = "com.rido"
version = "0.1.0"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
}

dependencies {

    // ----------------------------
    // SPRING BOOT BASICS
    // ----------------------------
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // ----------------------------
    // DATABASE (JPA + POSTGRES)
    // ----------------------------
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.postgresql:postgresql:42.6.0")

    // ----------------------------
    // REDIS (Lettuce)
    // ----------------------------
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.lettuce:lettuce-core:6.2.4.RELEASE")

    // ----------------------------
    // SECURITY / PASSWORD ENCODING
    // ----------------------------
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-crypto")
implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // ----------------------------
    // JWT
    // ----------------------------
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")

    // ----------------------------
    // JACKSON (Java Time Support)
    // ----------------------------
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // ----------------------------
    // LOMBOK
    // ----------------------------
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // ----------------------------
    // TESTS
    // ----------------------------
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.opentelemetry:opentelemetry-api:1.41.0")
    




}

tasks.withType<Test> {
    useJUnitPlatform()
}

/**
 * FIX: JVM TIMEZONE
 * Prevents PostgreSQL error:
 *   "FATAL: invalid value for parameter TimeZone: 'Asia/Calcutta'"
 *
 * Because Windows uses Asia/Calcutta (deprecated), but PostgreSQL accepts only Asia/Kolkata.
 */
tasks.bootRun {
    jvmArgs = listOf("-Duser.timezone=Asia/Kolkata")
}
