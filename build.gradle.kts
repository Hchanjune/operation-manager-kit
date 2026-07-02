import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    kotlin("jvm") version "2.3.21" apply false
    kotlin("plugin.spring") version "2.3.21" apply false
}

allprojects {

    group = "com.github.Hchanjune.operation-manager-kit"

    version = "0.8.5"

    repositories {
        mavenCentral()
    }
}

apply(plugin = "jacoco")

tasks.register<JacocoReport>("jacocoAggregatedReport") {
    group = "verification"
    description = "Aggregated JaCoCo report: unit + integration test coverage for core, servlet, and reactive"
    dependsOn(subprojects.map { ":${it.name}:test" })
    executionData.from(subprojects.map { it.layout.buildDirectory.file("jacoco/test.exec") })
    reports {
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/aggregated/html"))
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/aggregated/jacocoTestReport.xml"))
    }
}

gradle.projectsEvaluated {
    tasks.named<JacocoReport>("jacocoAggregatedReport") {
        listOf(":core", ":servlet", ":reactive").forEach { path ->
            val sub = project(path)
            val main = sub.extensions.getByType(JavaPluginExtension::class.java).sourceSets["main"]
            sourceDirectories.from(main.allSource.srcDirs)
            classDirectories.from(main.output.classesDirs)
        }
    }

    tasks.register<JacocoCoverageVerification>("jacocoCoverageGate") {
        group = "verification"
        description = "Fails the build if aggregated coverage drops below threshold"
        dependsOn("jacocoAggregatedReport")
        executionData.from(subprojects.map { it.layout.buildDirectory.file("jacoco/test.exec") })
        listOf(":core", ":servlet", ":reactive").forEach { path ->
            val sub = project(path)
            val main = sub.extensions.getByType(JavaPluginExtension::class.java).sourceSets["main"]
            sourceDirectories.from(main.allSource.srcDirs)
            classDirectories.from(main.output.classesDirs)
        }
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
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "jacoco")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
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
