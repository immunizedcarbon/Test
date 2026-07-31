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
        val repositoryRoot = appRoot.parentFile
        val patchRoot = repositoryRoot.resolve("optilink-v3/patches")

        fun install(name: String, relativeTarget: String) {
            val source = patchRoot.resolve(name)
            check(source.isFile) { "Missing V3 patch: $name" }
            val target = appRoot.resolve(relativeTarget)
            target.parentFile.mkdirs()
            source.copyTo(target, overwrite = true)
        }

        appRoot.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application") apply false
            }
            """.trimIndent() + "\n"
        )
        appRoot.resolve("gradle.properties").writeText(
            """
            org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
            android.nonTransitiveRClass=true
            android.nonFinalResIds=true
            """.trimIndent() + "\n"
        )

        install("MainActivity.kt", "app/src/main/java/de/oai/optilink/android/MainActivity.kt")
        install("CameraReceiver.kt", "app/src/main/java/de/oai/optilink/android/CameraReceiver.kt")
        install("SenderEngine.kt", "app/src/main/java/de/oai/optilink/android/SenderEngine.kt")
        install("ReceiverEngine.kt", "app/src/main/java/de/oai/optilink/android/ReceiverEngine.kt")
        install("OpticalViews.kt", "app/src/main/java/de/oai/optilink/android/OpticalViews.kt")
        install("BurstQr.kt", "app/src/main/java/de/oai/optilink/core/BurstQr.kt")
        install("BurstQrTest.kt", "app/src/test/java/de/oai/optilink/core/BurstQrTest.kt")
        install("app-build.gradle.kts", "app/build.gradle.kts")
        install("AndroidManifest.xml", "app/src/main/AndroidManifest.xml")

        val obsolete = listOf(
            "app/src/main/java/de/oai/optilink/android/ColorFrameDecoder.kt",
            "app/src/main/java/de/oai/optilink/android/Homography.kt",
            "app/src/main/java/de/oai/optilink/android/MarkerDetector.kt",
            "app/src/main/java/de/oai/optilink/android/OpticalFrameView.kt",
            "app/src/main/java/de/oai/optilink/android/OpticalLayout.kt",
            "app/src/main/java/de/oai/optilink/android/ReceiverOverlayView.kt",
            "app/src/main/java/de/oai/optilink/android/YuvFrame.kt",
            "app/src/main/java/de/oai/optilink/core/GridCodec.kt",
            "app/src/main/java/de/oai/optilink/core/ReedSolomon.kt",
            "app/src/main/java/de/oai/optilink/core/WireHeader.kt",
            "app/src/main/java/de/oai/optilink/core/Profiles.kt",
        )
        obsolete.forEach { appRoot.resolve(it).delete() }

        val keyText = patchRoot.resolve("optilink-private-test.keystore.b64").readText().trim()
        appRoot.resolve("optilink-private-test.keystore").writeBytes(java.util.Base64.getDecoder().decode(keyText))
    }
}
