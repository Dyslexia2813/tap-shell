plugins {
    id("com.android.application")
}

android {
    namespace = "online.teamdream.tap_shell"
    compileSdk = 34

    defaultConfig {
        applicationId = "online.teamdream.tap_shell"
        minSdk = 29
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0"
    }

    // Фиксированный ключ подписи, чтобы новые сборки ставились поверх старых.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.activity:activity:1.9.0")
    implementation("androidx.core:core:1.13.1")
}
