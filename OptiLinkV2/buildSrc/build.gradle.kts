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
        var source = mainActivity.readText()

        val cameraAnchor = "            textureView = texture,\n            onFrame = { decoded ->"
        check(cameraAnchor in source) { "MainActivity camera construction anchor not found" }
        source = source.replace(
            cameraAnchor,
            "            textureView = texture,\n            decoder = ColorFrameDecoder(),\n            onFrame = { decoded ->",
        )

        val oldStatus = "        val status = text(\"Datei wird vorbereitet …\", 14f, bold = true, color = Color.WHITE)"
        val newStatus = listOf(
            "        val status = text(\"Datei wird vorbereitet …\", 14f, bold = true, color = Color.WHITE).apply {",
            "            maxLines = 2",
            "            setPadding(0, 0, dp(12), 0)",
            "        }",
        ).joinToString("\n")
        check(oldStatus in source) { "Sender status anchor not found" }
        source = source.replace(oldStatus, newStatus)

        val oldRoot = listOf(
            "        val root = FrameLayout(this).apply {",
            "            setBackgroundColor(Color.BLACK)",
            "            addView(frameView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))",
            "            addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {",
            "                setMargins(dp(12), dp(12), dp(12), dp(12))",
            "            })",
            "        }",
        ).joinToString("\n")
        val newRoot = listOf(
            "        val root = LinearLayout(this).apply {",
            "            orientation = LinearLayout.VERTICAL",
            "            setBackgroundColor(Color.BLACK)",
            "            addView(frameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))",
            "            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {",
            "                setMargins(dp(12), dp(8), dp(12), dp(12))",
            "            })",
            "        }",
        ).joinToString("\n")
        check(oldRoot in source) { "Sender overlay layout anchor not found" }
        source = source.replace(oldRoot, newRoot)
        source = source.replaceFirst("setColor(0xcc101318.toInt())", "setColor(0xff101318.toInt())")
        mainActivity.writeText(source)

        val appBuild = appRoot.resolve("app/build.gradle.kts")
        var buildSource = appBuild.readText()
        check("versionCode = 2" in buildSource && "versionName = \"2.0.0\"" in buildSource) {
            "Version 2.0.0 anchors not found"
        }
        buildSource = buildSource
            .replace("versionCode = 2", "versionCode = 3")
            .replace("versionName = \"2.0.0\"", "versionName = \"2.0.1\"")
        appBuild.writeText(buildSource)
    }
}
