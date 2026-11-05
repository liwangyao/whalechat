plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.whalemate.wechat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.whalemate.wechat"
        minSdk = 26
        targetSdk = 34
        versionCode = 15
        versionName = "0.4.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
}
