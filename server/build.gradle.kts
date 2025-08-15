import org.jetbrains.kotlin.ir.backend.js.compile

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "yangfentuozi.batteryrecorder.server"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        aidl = true
    }
}

dependencies {

    implementation(libs.androidx.annotation)

    compileOnly(project(":hiddenapi"))
}