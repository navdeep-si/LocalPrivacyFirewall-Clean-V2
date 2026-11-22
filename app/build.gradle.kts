plugins {
    id("com.android.application")
}

android {
    namespace = "com.localprivacy.firewall"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.localprivacy.firewall"
        minSdk = 34   // Android 14
        targetSdk = 36 // Android 16 target; compatible with Android 17 at runtime
        versionCode = 2
        versionName = "2.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
