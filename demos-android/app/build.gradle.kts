plugins {
    id("com.android.application")
}
android {
    namespace = "org.demos.dvk"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.demos.dvk"
        minSdk = 37
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
