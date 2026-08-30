// Root build — declares plugins without applying them to the root project.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.dokka)
}

// Dokka multi-module API docs. Only the PUBLIC API modules are documented —
// android-tools (impl), ui (impl) and app (entry point) are internal layers
// and intentionally excluded from the API surface.
dependencies {
    dokka(project(":core:tool-contract"))
    dokka(project(":core:tool-registry"))
    dokka(project(":core:security"))
    dokka(project(":core:model-adapter"))
    dokka(project(":app:agent-runtime"))
}

dokka {
    dokkaPublications.html {
        moduleName.set("AgentAI API")
        outputDirectory.set(layout.buildDirectory.dir("docs/api"))
        includes.from("README.md")
    }
}