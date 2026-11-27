plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class SyncTvUpdateApkTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val tvApkFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copyApk() {
        val target = outputDir.file("updates/casatv-tv.apk").get().asFile
        target.parentFile.mkdirs()
        tvApkFile.get().asFile.copyTo(target, overwrite = true)
    }
}

android {
    namespace = "it.casatv.server"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.casatv.server"
        // Android 8.0 (2017) and newer: this includes every Android 12+ device
        // while still running on older boxes and phones.
        minSdk = 26
        targetSdk = 35
        versionCode = 2611058
        versionName = "26.11.058"
        buildConfigField("String", "GITHUB_REPO", "\"Natsvkashii/CasaTV\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("casatv-official.jks")
            storePassword = "casatv2026"
            keyAlias = "casatv"
            keyPassword = "casatv2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val syncTask = tasks.register<SyncTvUpdateApkTask>(
            "sync${variant.name.replaceFirstChar { it.uppercase() }}TvUpdateApk",
        ) {
            dependsOn(":app:assembleRelease")
            tvApkFile.set(project(":app").layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(syncTask, SyncTvUpdateApkTask::outputDir)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jmdns:jmdns:3.5.9")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
