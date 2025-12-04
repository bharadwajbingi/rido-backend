// Root build file for Rido Backend Monorepo
// Individual service configurations are in their respective build.gradle.kts files

plugins {
    base
}

allprojects {
    group = "com.rido"
    version = "0.1.0"
}

tasks.register("cleanAll") {
    dependsOn(subprojects.map { it.tasks.named("clean") })
}
