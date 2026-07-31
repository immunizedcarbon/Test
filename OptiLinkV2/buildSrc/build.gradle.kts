plugins {
    `java-gradle-plugin`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:9.3.0")
}

gradlePlugin {
    plugins {
        create("androidApplicationShim") {
            id = "com.android.application"
            implementationClass = "de.oai.build.AndroidApplicationShim"
        }
    }
}
