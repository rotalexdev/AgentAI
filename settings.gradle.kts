pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AgentAI"

// JVM-testable pure-Kotlin core (zero Android deps)
include(":core:tool-contract")
include(":core:tool-registry")
include(":core:security")
include(":core:model-adapter")

// Android layer
include(":app:agent-runtime")
include(":app:android-tools")
include(":app:whisper")
include(":app:ui")
include(":app:app")