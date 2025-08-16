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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        aidl = true
    }
}

dependencies {

    implementation(libs.androidx.annotation)

    compileOnly(project(":hiddenapi"))
}