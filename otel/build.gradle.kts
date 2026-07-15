plugins {
    kotlin("jvm")
    `maven-publish`
}

val springBootVersion: String by project

dependencies {
    api(project(":core"))

    compileOnly(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    compileOnly("io.opentelemetry:opentelemetry-api")

    testImplementation(kotlin("test"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    testImplementation("io.opentelemetry:opentelemetry-api")
    testImplementation("io.opentelemetry:opentelemetry-sdk")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    testRuntimeOnly("org.slf4j:slf4j-simple")
}

tasks.test { useJUnitPlatform() }
