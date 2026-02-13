plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("kapt")
    `maven-publish`
}

dependencies {
    implementation(project(":core"))

    // BOM은 compileOnly 쪽(스프링 의존성들) 버전 정합용으로만 사용해도 됨
    compileOnly(platform("org.springframework.boot:spring-boot-dependencies:4.0.2"))

    // ✅ processor는 버전을 직접 명시 (kapt에 BOM이 잘 안 먹는 케이스 회피)
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