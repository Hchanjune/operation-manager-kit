plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("kapt")
    `maven-publish`
}

val springBootVersion: String by project

dependencies {
    api(project(":core"))

    compileOnly(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))

    kapt("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")

    compileOnly("org.slf4j:slf4j-api")
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    compileOnly("jakarta.annotation:jakarta.annotation-api")
    compileOnly("org.springframework:spring-webflux")
    compileOnly("org.springframework:spring-aop")
    compileOnly("org.aspectj:aspectjrt")
    compileOnly("org.springframework.security:spring-security-core")
    compileOnly("org.springframework.security:spring-security-web")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("io.opentelemetry:opentelemetry-api")

    testImplementation(kotlin("test"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    testImplementation("org.slf4j:slf4j-api")
    testRuntimeOnly("org.slf4j:slf4j-simple")
    testImplementation("io.opentelemetry:opentelemetry-api")
    testImplementation("org.aspectj:aspectjrt")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    testImplementation("org.springframework.security:spring-security-core")
    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework:spring-webflux")
}

tasks.test { useJUnitPlatform() }

tasks.named<Jar>("jar") {
    enabled = true
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(layout.buildDirectory.dir("tmp/kapt3/classes/main")) {
        include("META-INF/spring-configuration-metadata.json")
    }
}
