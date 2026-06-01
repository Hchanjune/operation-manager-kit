plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

val springBootVersion: String by project

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(project(":spring-webflux"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.aspectj:aspectjweaver")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
}

tasks.test { useJUnitPlatform() }

// test-app-webflux is not published
publishing { publications.clear() }
