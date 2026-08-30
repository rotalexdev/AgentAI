plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.agentai.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.agentai.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":app:ui"))
    implementation(project(":app:android-tools"))
    implementation(project(":app:agent-runtime"))
    implementation(project(":core:tool-contract"))
    implementation(project(":core:tool-registry"))
    implementation(project(":core:security"))
    implementation(project(":core:model-adapter"))

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
}