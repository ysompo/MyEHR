import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    // Screens, data and sync logic shared with the Android app.
    sourceSets["main"].kotlin.srcDir("../common/src/main/kotlin")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("com.anthropic:anthropic-java:3.2.0")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("com.github.javakeyring:java-keyring:1.0.4")

    testImplementation(kotlin("test"))
}

val appVersion = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"

compose.desktop {
    application {
        mainClass = "com.myehr.app.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "MyEHR"
            packageVersion = appVersion
            description = "MyEHR – family medical file"
            vendor = "MyEHR"
            modules("java.sql", "java.prefs", "java.net.http", "jdk.crypto.ec")
            windows { menu = true; shortcut = true; upgradeUuid = "6c2a7a3e-6f0b-4f1a-9b8e-2f6a1c0d7e11" }
            macOS { bundleID = "com.myehr.desktop" }
        }
    }
}
