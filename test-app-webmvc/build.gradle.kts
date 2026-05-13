plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

val springBootVersion: String by project

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(project(":spring-webmvc"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.aspectj:aspectjweaver")
}

tasks.test { useJUnitPlatform() }

// test-app-webmvc is not published
publishing { publications.clear() }
