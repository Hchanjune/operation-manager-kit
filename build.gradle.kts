import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoCoverageReport
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    kotlin("jvm") version "2.3.21" apply false
    kotlin("plugin.spring") version "2.3.21" apply false
    `jacoco-report-aggregation`
}

allprojects {

    group = "com.github.Hchanjune.operation-manager-kit"

    version = "0.11.3"

    repositories {
        mavenCentral()
    }
}

dependencies {
    // Every module whose "test" suite runs — the test apps cover servlet/reactive via integration tests.
    // "test" 스위트가 도는 모든 모듈 — test-app 은 통합 테스트로 servlet/reactive 를 커버한다.
    jacocoAggregation(project(":core"))
    jacocoAggregation(project(":servlet"))
    jacocoAggregation(project(":reactive"))
    jacocoAggregation(project(":test-app-webmvc"))
    jacocoAggregation(project(":test-app-webflux"))
}

reporting {
    reports {
        create<JacocoCoverageReport>("jacocoAggregatedReport") {
            testSuiteName = "test"
        }
    }
}

tasks.named<JacocoReport>("jacocoAggregatedReport") {
    group = "verification"
    description = "Aggregated JaCoCo report: unit + integration test coverage for core, servlet, and reactive"
    reports {
        html.required = true
        html.outputLocation = layout.buildDirectory.dir("reports/jacoco/aggregated/html")
        xml.required = true
        xml.outputLocation = layout.buildDirectory.file("reports/jacoco/aggregated/jacocoTestReport.xml")
    }
}

tasks.register<JacocoCoverageVerification>("jacocoCoverageGate") {
    group = "verification"
    description = "Fails the build if aggregated coverage drops below threshold"
    val report = tasks.named<JacocoReport>("jacocoAggregatedReport").get()
    executionData.from(report.executionData)
    sourceDirectories.from(report.sourceDirectories)
    classDirectories.from(report.classDirectories)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

subprojects {
    pluginManager.apply("java-library")
    pluginManager.apply("org.jetbrains.kotlin.jvm")
    pluginManager.apply("maven-publish")
    pluginManager.apply("jacoco")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict")
        }
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
            }
        }
    }
}
