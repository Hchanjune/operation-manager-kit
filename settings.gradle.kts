plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "operation-manager-kit"
include("core")
include("spring-webmvc")
include("spring-webflux")
include("test-app-webmvc")
include("test-app-webflux")