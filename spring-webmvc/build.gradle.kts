plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `maven-publish`
}

val bootBomVersion = "4.0.2" // Spring Boot BOM stable :contentReference[oaicite:1]{index=1}

dependencies {
    implementation(project(":core"))

    // ✅ BOM(버전관리)만 가져오고, 아래 개별 의존성엔 버전을 적지 않습니다.
    compileOnly(platform("org.springframework.boot:spring-boot-dependencies:$bootBomVersion"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:$bootBomVersion"))

    // Logging API (MDC)
    compileOnly("org.slf4j:slf4j-api")

    // Servlet API (HttpServletRequest/Response)
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    // @PostConstruct 등
    compileOnly("jakarta.annotation:jakarta.annotation-api")

    // Spring MVC
    compileOnly("org.springframework:spring-webmvc")

    // Spring AOP + Aspect (@Aspect)
    compileOnly("org.springframework:spring-aop")
    compileOnly("org.aspectj:aspectjrt")

    // Spring Security (IssuerProvider 구현 시)
    compileOnly("org.springframework.security:spring-security-core")

    // Boot AutoConfiguration 사용 시
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
