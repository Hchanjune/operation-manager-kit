plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(project(":spring-webmvc"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.2"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.aspectj:aspectjweaver")
}

tasks.test { useJUnitPlatform() }

// test-app-webmvc is not published
publishing { publications.clear() }
