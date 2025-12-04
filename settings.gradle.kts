pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "rido"

// Phase 3: Services moved to services/ folder
include("services:auth")
include("services:gateway")
include("services:profile")
