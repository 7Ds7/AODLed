import java.util.Properties

plugins {
    id("com.android.application")
}

val appVersion = "0.1"

// Release signing details live outside git: storeFile, storePassword, keyAlias, keyPassword
val keystore = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

keystore?.let { p ->
    val missing = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").filter { p.getProperty(it).isNullOrBlank() }
    if (missing.isNotEmpty()) throw GradleException("keystore.properties is missing ${missing.joinToString()}")
    if (!file(p.getProperty("storeFile")).exists()) throw GradleException("keystore.properties: no file at storeFile")
}

// Unsigned is right for F-Droid's own build, but that APK isn't one to publish
if (keystore == null) tasks.matching { it.name == "assembleRelease" }.configureEach {
    doLast { logger.warn("Release APK is unsigned: no keystore.properties") }
}

android {
    namespace = "com.binaryunit.aodled"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.binaryunit.aodled"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = appVersion
    }

    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore.getProperty("storeFile"))
            storePassword = keystore.getProperty("storePassword")
            keyAlias = keystore.getProperty("keyAlias")
            keyPassword = keystore.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // No Google-encrypted dependency list in the APK signing block: F-Droid flags it, and it's unreadable to anyone else
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

// APKs come out as AODLed-<version>-<debug|release>.apk
base {
    archivesName.set("AODLed-$appVersion")
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}
