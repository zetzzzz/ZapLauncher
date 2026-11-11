plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zetzzzz.zaplauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zetzzzz.zaplauncher"
        minSdk = 28
        targetSdk = 34
        versionCode = 2
        versionName = "0.1.1"
    }

    signingConfigs {
        // Release signing is configured from ~/.gradle/gradle.properties
        // (zap.keystore.path / .password / .alias / .keyPassword).
        // Without those properties the release APK is built unsigned.
        create("zap") {
            val ksPath = project.findProperty("zap.keystore.path") as String?
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = project.findProperty("zap.keystore.password") as String?
                keyAlias = project.findProperty("zap.keystore.alias") as String?
                keyPassword = project.findProperty("zap.keystore.keyPassword") as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only sign when the local keystore actually exists.
            val ksFile = project.findProperty("zap.keystore.path")
                ?.let { file(it as String) }
            if (ksFile != null && ksFile.exists()) {
                signingConfig = signingConfigs.getByName("zap")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // 1.0.0 is the newest leanback that still works with AGP 8.5.2 +
    // compileSdk 34; 1.1.0 was never released stable and 1.2.0 needs
    // AGP 8.6+ and compileSdk 35.
    implementation("androidx.leanback:leanback:1.0.0")
}
