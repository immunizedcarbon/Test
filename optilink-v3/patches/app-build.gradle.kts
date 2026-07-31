plugins {
    id("com.android.application")
}

android {
    namespace = "de.oai.optilink"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.oai.optilink"
        minSdk = 35
        targetSdk = 36
        versionCode = 4
        versionName = "3.0.0"
    }

    signingConfigs {
        create("privateTest") {
            storeFile = rootProject.file("optilink-private-test.keystore")
            storePassword = "optilink-private"
            keyAlias = "optilink"
            keyPassword = "optilink-private"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }

    testOptions {
        unitTests.all { it.useJUnit() }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("privateTest")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("privateTest")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation("com.google.zxing:core:3.5.4")
    testImplementation("junit:junit:4.13.2")
}

tasks.named("assembleDebug") {
    dependsOn("testDebugUnitTest")
}
