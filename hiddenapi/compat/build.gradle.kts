plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "yangfentuozi.hiddenapi.compat"
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
}

dependencies {

    implementation(libs.androidx.annotation)

    compileOnly(project(":hiddenapi:stub"))
}