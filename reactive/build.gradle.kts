plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("kapt")
    `maven-publish`
}

val springBootVersion: String by project

dependencies {
    api(project(":core"))
    implementation(project(":otel"))

    compileOnly(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))

    kapt("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")

    // Minimum versions (Spring Boot 3.2 managed) — the consuming app's BOM overrides them.
    // 최소 버전(Spring Boot 3.2 관리 버전) — 사용처 BOM 이 있으면 그쪽이 덮어쓴다.
    api("org.jetbrains.kotlin:kotlin-reflect:2.3.21") // matches kotlin-stdlib / kotlin-stdlib 과 맞춤
    api("org.slf4j:slf4j-api:2.0.16")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3")
    api("jakarta.annotation:jakarta.annotation-api:2.1.1")

    compileOnly("org.aspectj:aspectjrt")
    compileOnly("org.springframework:spring-aop")

    compileOnly("io.micrometer:micrometer-core")
    compileOnly("org.springframework:spring-webflux")
    compileOnly("org.springframework.security:spring-security-core")
    compileOnly("org.springframework.security:spring-security-web")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("io.opentelemetry:opentelemetry-api")
    // Optional at runtime: bridged spans are written into the Reactor context so OTel-
    // instrumented reactive clients (WebClient, R2DBC, ...) nest under OMK spans.
    compileOnly("io.opentelemetry.instrumentation:opentelemetry-reactor-3.1:2.16.0-alpha")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    testImplementation("org.slf4j:slf4j-api")
    testRuntimeOnly("org.slf4j:slf4j-simple")
    testImplementation("io.opentelemetry:opentelemetry-api")
    testImplementation("io.opentelemetry:opentelemetry-sdk")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    // Phase 2(b) prototype: Reactor-context propagation the way OTel-instrumented clients read it
    testImplementation("io.opentelemetry.instrumentation:opentelemetry-reactor-3.1:2.16.0-alpha")
    testImplementation("org.aspectj:aspectjrt")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    testImplementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework:spring-webflux")
    testImplementation("org.springframework:spring-messaging")
    testImplementation("org.apache.kafka:kafka-clients")
    testImplementation("io.projectreactor:reactor-test")
}

tasks.test { useJUnitPlatform() }

tasks.named<Jar>("jar") {
    enabled = true
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(layout.buildDirectory.dir("tmp/kapt3/classes/main")) {
        include("META-INF/spring-configuration-metadata.json")
    }
}
