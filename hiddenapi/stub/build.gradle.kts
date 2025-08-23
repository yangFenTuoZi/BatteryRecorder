plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "hiddenapi.stub"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        aidl = true
    }
}

dependencies {

    implementation(libs.androidx.annotation)
}