from pathlib import Path
ROOT = Path("optilink-v2/project")
ROOT.mkdir(parents=True, exist_ok=True)

p = ROOT / 'settings.gradle.kts'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text(r'''pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "OptiLinkV2"
include(":app")
''', encoding='utf-8')

p = ROOT / 'build.gradle.kts'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text(r'''plugins {
    id("com.android.application") version "9.4.0" apply false
}
''', encoding='utf-8')

p = ROOT / 'gradle.properties'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text(r'''org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
android.useAndroidX=false
android.nonTransitiveRClass=true
android.nonFinalResIds=true
''', encoding='utf-8')

p = ROOT / 'app/build.gradle.kts'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text(r'''plugins {
    id("com.android.application")
}

android {
    namespace = "de.oai.optilink"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.oai.optilink"
        minSdk = 35
        targetSdk = 37
        versionCode = 2
        versionName = "2.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}
''', encoding='utf-8')

p = ROOT / 'app/proguard-rules.pro'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text(r'''# No reflection-based runtime. R8 may optimize the whole application.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
''', encoding='utf-8')

p = ROOT / 'app/src/main/AndroidManifest.xml'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera.any" android:required="false" />

    <application
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="false"
        android:icon="@mipmap/ic_launcher"
        android:label="OptiLink"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:usesCleartextTraffic="false"
        android:theme="@style/AppTheme">
        <activity
            android:name=".android.MainActivity"
            android:configChanges="keyboardHidden|orientation|screenSize"
            android:exported="true"
            android:screenOrientation="unspecified">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
''', encoding='utf-8')

p = ROOT / 'app/src/main/res/drawable/ic_launcher_foreground.xml'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text(r'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FFFFFF" android:pathData="M25,25h58v58h-58z" />
    <path android:fillColor="#00D7E8" android:pathData="M31,31h18v18h-18zM59,59h18v18h-18z" />
    <path android:fillColor="#ED3CFF" android:pathData="M59,31h18v18h-18zM31,59h18v18h-18z" />
    <path android:fillColor="#0B0E12" android:pathData="M49,31h10v46h-10zM31,49h46v10h-46z" />
</vector>
''', encoding='utf-8')

p = ROOT / 'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
''', encoding='utf-8')

p = ROOT / 'app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
''', encoding='utf-8')

p = ROOT / 'app/src/main/res/values/colors.xml'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="launcher_background">#0B0E12</color>
</resources>
''', encoding='utf-8')

p = ROOT / 'app/src/main/res/values/styles.xml'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:statusBarColor">#F6F7F9</item>
        <item name="android:navigationBarColor">#F6F7F9</item>
        <item name="android:colorAccent">#275DFF</item>
        <item name="android:windowActionModeOverlay">true</item>
        <item name="android:windowNoTitle">true</item>
    </style>
</resources>
''', encoding='utf-8')

p = ROOT / 'app/src/main/res/xml/data_extraction_rules.xml'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup disableIfNoEncryptionCapabilities="true">
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
    </device-transfer>
</data-extraction-rules>
''', encoding='utf-8')
