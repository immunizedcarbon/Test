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

tasks.named("jar") {
    doLast {
        val appRoot = projectDir.parentFile
        appRoot.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application") apply false
            }
            """.trimIndent() + "\n"
        )

        val mainActivity = appRoot.resolve("app/src/main/java/de/oai/optilink/android/MainActivity.kt")
        val source = mainActivity.readText()
        val anchor = "            textureView = texture,\n            onFrame = { decoded ->"
        if (anchor !in source) {
            error("MainActivity camera construction anchor not found")
        }
        mainActivity.writeText(
            source.replace(
                anchor,
                "            textureView = texture,\n            decoder = ColorFrameDecoder(),\n            onFrame = { decoded ->",
            )
        )
    }
}
