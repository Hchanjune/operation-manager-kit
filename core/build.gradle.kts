plugins {
    kotlin("jvm")
    `maven-publish`
}

dependencies {
    api("org.slf4j:slf4j-api:2.0.16")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
