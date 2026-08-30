plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.agentai.app.tools"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true // Robolectric
            all { it.useJUnitPlatform() }
        }
    }
}

dependencies {
    api(project(":core:tool-contract"))
    api(project(":core:tool-registry"))
    api(project(":core:security"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}