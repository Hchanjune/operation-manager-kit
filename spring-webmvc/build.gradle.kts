plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("kapt")
    `maven-publish`
}

dependencies {
    api(project(":core"))

    compileOnly(platform("org.springframework.boot:spring-boot-dependencies:4.0.2"))

    kapt("org.springframework.boot:spring-boot-configuration-processor:4.0.2")

    compileOnly("org.slf4j:slf4j-api")
    compileOnly("io.micrometer:micrometer-core")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("jakarta.annotation:jakarta.annotation-api")
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("org.springframework:spring-aop")
    compileOnly("org.aspectj:aspectjrt")
    compileOnly("org.springframework.security:spring-security-core")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("io.opentelemetry:opentelemetry-api")

    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

// kapt Metadata Injection
tasks.named<Jar>("jar") {
    enabled = true
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(layout.buildDirectory.dir("tmp/kapt3/classes/main")) {
        include("META-INF/spring-configuration-metadata.json")
    }
}